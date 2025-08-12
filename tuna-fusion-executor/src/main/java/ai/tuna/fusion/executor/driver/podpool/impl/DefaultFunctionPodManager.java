package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.executor.driver.podpool.*;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.CustomResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.*;

/**
 * @author robinqu
 */
@Slf4j
public class DefaultFunctionPodManager implements FunctionPodManager {
    private final PodPoolConnectorFactory podPoolConnectorFactory;
    private final Cache<String, CountedPodAccess> podAccessCache;
    private final PodPoolResources podPoolResources;

    public DefaultFunctionPodManager(PodPoolConnectorFactory podPoolConnectorFactory, PodPoolResources podPoolResources) {
        this.podPoolConnectorFactory = podPoolConnectorFactory;
        this.podPoolResources = podPoolResources;
        this.podAccessCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    }

    private String cacheKey(PodFunction function, PodPool podPool) throws FunctionPodAccessException {
        return Optional.ofNullable(function.getStatus())
                .map(PodFunctionStatus::getEffectiveBuild)
                .map(PodFunctionStatus.BuildInfo::getUid)
                .map(this::cacheKey)
                .orElseThrow(()-> new FunctionPodAccessException("Cannot find effectiveBuild", podPool, function));
    }

    private String cacheKey(PodAccess podAccess) {
        var uid =  podAccess.getFunctionBuildUid();
        return cacheKey(uid);
    }

    private String cacheKey(Pod pod) {
        var uid = pod.getMetadata().getLabels().get(PodPool.SPECIALIZED_POD_FUNCTION_BUILD_ID_LABEL_VALUE);
        return cacheKey(uid);
    }

    private String cacheKey(String buildUid) {
        return cacheKey(buildUid, Instant.now().getEpochSecond() % POD_ACCESS_PER_BUILD);
    }

    private String cacheKey(String buildUid, long idx) {
        return buildUid + "-" + idx;
    }

    @Scheduled(fixedRate = 1000 * 30)
    private void cleanupOrphanPods() {
        var podPoolInformers = podPoolResources.podPool().getSharedIndexInformers();
        if (podPoolInformers.isEmpty()) {
            log.info("[cleanupOrphanPods] No podPool informer found");
            return;
        }
        for (var sharedInformer : podPoolInformers) {
            for (var podPool: sharedInformer.getStore().list()) {
                var podPoolKey = ResourceUtils.computeResourceMetaKey(podPool);
                var t1 = Instant.now().toEpochMilli();
                var client = podPoolResources.getKubernetesClient();
                var specializedPods = PodPoolResourceUtils.listSpecializedPods(podPool, client);
                var outdatedCount = 0;
                var expiredCount = 0;
                var counterLimitReachedCount = 0;
                for (var pod : specializedPods) {
                    try {
                        var isOutdatedBuild = hasOutdatedBuild(pod);
                        var isExpired = isExpiredPod(pod, podPool.getSpec().getTtlPerPod());
                        var isCounterExceeded = isCounterExceeded(pod);
                        if (isOutdatedBuild) {
                            outdatedCount++;
                        }
                        if (isExpired) {
                            expiredCount++;
                        }
                        if (isCounterExceeded) {
                            counterLimitReachedCount++;
                        }
                        if (isOutdatedBuild || isExpired || isCounterExceeded) {
                            var access = podAccessCache.getIfPresent(cacheKey(pod));
                            var cached = Objects.nonNull(access);
                            log.info("[cleanupOrphanPods] podPool={}, pod={}, cached={}, isCounterExceeded={}, isOutdatedBuild={}, isExpired={}", podPool.getMetadata().getName(), pod.getMetadata().getName(), cached, isCounterExceeded, isOutdatedBuild, isExpired);
                            doDisposeAccess(PodAccess.builder()
                                    .namespace(pod.getMetadata().getNamespace())
                                    .selectedPod(pod)
                                    .podTtlInSeconds(podPool.getSpec().getTtlPerPod())
                                    .functionName(pod.getMetadata().getLabels().get(PodPool.SPECIALIZED_POD_FUNCTION_NAME_LABEL_VALUE))
                                    .functionBuildUid(pod.getMetadata().getLabels().get(PodPool.SPECIALIZED_POD_FUNCTION_BUILD_ID_LABEL_VALUE))
                                    .podPoolName(podPool.getMetadata().getName())
                                    .build());
                        }
                    } catch (Exception e) {
                        log.error("Exception occurred during checking specialized pod {}: {}", pod, e.getMessage(), e);
                    }
                }
                log.info("[cleanupOrphanPods] PodPool {}, specializedPods.size()={}, outdated={}, expired={}, counterLimitReached={}, elapsed={}ms", podPoolKey, specializedPods.size(), outdatedCount, expiredCount, counterLimitReachedCount, Instant.now().toEpochMilli() - t1);
            }
        }


    }

    /**
     * Check counter once more in case some CountedPodAccess is not closed properly
     */
    private boolean isCounterExceeded(Pod pod) {
        return Optional.ofNullable(podAccessCache.getIfPresent(cacheKey(pod)))
                .map(countedPodAccess -> countedPodAccess.getUsageCount() != null && countedPodAccess.getUsageCount().intValue() >= countedPodAccess.getMaxUsageCount())
                .orElse(false);
    }

    /**
     * Evict pods that has out-dated build
     */
    private boolean hasOutdatedBuild(Pod pod) {
        return Optional.ofNullable(pod.getMetadata().getLabels().get(PodPool.SPECIALIZED_POD_FUNCTION_NAME_LABEL_VALUE))
                .flatMap(fnName -> podPoolResources.queryPodFunction(pod.getMetadata().getNamespace(), fnName))
                .map(CustomResource::getStatus)
                .map(PodFunctionStatus::getEffectiveBuild)
                .map(PodFunctionStatus.BuildInfo::getUid)
                .map(buildUid -> !Strings.CS.equals(pod.getMetadata().getLabels().get(PodPool.SPECIALIZED_POD_FUNCTION_BUILD_ID_LABEL_VALUE), buildUid))
                .orElse(false);
    }


    /**
     * Limit the lifespan of a specialized pod
     */
    private boolean isExpiredPod(Pod pod, Long ttlInSeconds) {
        var ttl = Optional.ofNullable(ttlInSeconds)
                .filter(v -> v > 0)
                .orElse(TTL_IN_SECONDS_FOR_SPECIALIZED_POD);
        var creationTime = Instant.parse(pod.getMetadata().getCreationTimestamp());
        return creationTime.isBefore(Instant.now().minusSeconds(ttl));
    }

    private boolean shouldDisposePodAccess(PodAccess podAccess) {
        var isExpired = isExpiredPod(podAccess.getSelectedPod(), podAccess.getPodTtlInSeconds());
        var isCounterExceeded = isCounterExceeded(podAccess.getSelectedPod());
        var isOutdated = hasOutdatedBuild(podAccess.getSelectedPod());
        log.debug("[shouldDisposePodAccess] podAccess={}, isExpired={}, isCounterExceeded={}, isOutdated={}", podAccess, isExpired, isCounterExceeded, isOutdated);
        return isExpired || isCounterExceeded || isOutdated;
    }

    @Override
    public CountedPodAccess requestAccess(PodFunction function, PodPool podPool) throws FunctionPodAccessException {
        var cacheKey = cacheKey(function, podPool);
        log.debug("[requestAccess] Requesting access: fn={}, cacheKey={}", ResourceUtils.computeResourceMetaKey(function), cacheKey);
        int retryCount = 0;
        int maxRetryCount = 3;

        while (retryCount++ < maxRetryCount) {
            CountedPodAccess access = null;
            try {
                 access = podAccessCache.get(cacheKey, () -> CountedPodAccess.builder()
                        .podAccess(podPoolConnectorFactory.get(podPool).requestAccess(function))
                        .maxUsageCount(Optional.ofNullable(podPool.getSpec().getRunPerPod()).filter(v -> v > 0).orElse(DEFAULT_RUN_PER_POD))
                        .usageCount(new AtomicInteger(0))
                        .functionPodManager(this)
                        .build());
            } catch (ExecutionException e) {
                if (Objects.nonNull(e.getCause()) && FunctionPodAccessException.class.isAssignableFrom(e.getCause().getClass())) {
                    // try another time and don't throw
                    log.warn("[requestAccess] Failed to request access to pod function {}. retryCount={}", function.getMetadata().getName(), retryCount, e);
                    continue;
                }
                throw new FunctionPodAccessException("Failed to request access to pod function " + function.getMetadata().getName(), podPool, function);
            }

            if (!shouldDisposePodAccess(access.getPodAccess())) {
                access.getUsageCount().incrementAndGet();
                log.debug("[requestAccess] Access acquired: pod={}, usageCount={}, maxUsageCount={}", ResourceUtils.computeResourceMetaKey(access.getPodAccess().getSelectedPod()), access.getUsageCount(), access.getMaxUsageCount());
                return access;
            } else {
                try {
                    doDisposeAccess(access.getPodAccess());
                } catch (FunctionPodDisposalException e) {
                    log.warn("[requestAccess] ignore exception when disposeAccess", e);
                }
                log.warn("[requestAccess] Orphan pod found {}. retryCount={}", ResourceUtils.computeResourceMetaKey(access.getPodAccess().getSelectedPod()), retryCount);
            }
        }
        throw new FunctionPodAccessException("Failed to request access to pod function %s after max retries %s".formatted(function.getMetadata().getName(), maxRetryCount), podPool, function);
    }

    @Override
    public void disposeAccess(CountedPodAccess countedPodAccess) throws FunctionPodDisposalException {
        var access = countedPodAccess.getPodAccess();
        if (shouldDisposePodAccess(access)) {
            doDisposeAccess(access);
        }
    }

    private void doDisposeAccess(PodAccess access) throws FunctionPodDisposalException {
        log.info("PodAccess is being evicted: {}", access);
        var podKey = ResourceUtils.computeResourceMetaKey(access.getSelectedPod());
        podAccessCache.invalidate(cacheKey(access));
        var connector = podPoolConnectorFactory.get(access.getNamespace(), access.getPodPoolName());
        connector.disposeAccess(access);
        log.info("Pod is evicted: {}", podKey);
    }

    @Override
    public List<CountedPodAccess> listAccess(PodFunction function, PodPool podPool) {
        return Optional.ofNullable(function.getStatus())
                .map(PodFunctionStatus::getEffectiveBuild)
                .map(buildInfo -> {
                    List<CountedPodAccess> podAccessList = new ArrayList<CountedPodAccess>();
                    for(int i=0;i<POD_ACCESS_PER_BUILD;i++) {
                        Optional.ofNullable(podAccessCache.getIfPresent(cacheKey(buildInfo.getUid(), i)))
                                .ifPresent(podAccessList::add);
                    }
                    return podAccessList;
                })
                .orElse(Collections.emptyList());
    }
}
