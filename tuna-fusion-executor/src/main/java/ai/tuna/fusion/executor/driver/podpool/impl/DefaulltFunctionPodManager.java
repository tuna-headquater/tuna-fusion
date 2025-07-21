package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.executor.driver.podpool.*;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import com.google.common.cache.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author robinqu
 */
@Slf4j
public class DefaulltFunctionPodManager implements FunctionPodManager {
    private final PodPoolConnectorFactory podPoolConnectorFactory;
    private final Cache<String, CountedPodAccess> podAccessCache;

    public DefaulltFunctionPodManager(PodPoolConnectorFactory podPoolConnectorFactory) {
        this.podPoolConnectorFactory = podPoolConnectorFactory;
        this.podAccessCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    }

    private String cacheKey(PodFunction function, PodPool podPool) throws FunctionPodAccessException {
        return Optional.ofNullable(function.getStatus())
                .map(PodFunctionStatus::getEffectiveBuild)
                .map(PodFunctionStatus.BuildInfo::getUid)
                .orElseThrow(()-> new FunctionPodAccessException("Cannot find effectiveBuild", podPool, function));
    }

    private String cacheKey(PodAccess podAccess) {
        return podAccess.getFunctionBuildUid();
    }

//    @Override
//    public void onRemoval(RemovalNotification<String, CountedPodAccess> notification) {
//        if (notification.getCause() == RemovalCause.EXPLICIT && notification.getValue() != null) {
//            try {
//                disposeAccess(notification.getValue());
//            } catch (FunctionPodDisposalException e) {
//                log.error("Failed to dispose pod {}",  notification.getValue().getPodAccess().getSelcetedPod().getMetadata().getName(), e);
//            }
//        }
//    }

    @Override
    public synchronized CountedPodAccess requestAccess(PodFunction function, PodPool podPool, String trailingPath) throws FunctionPodAccessException {
        var cacheKey = cacheKey(function, podPool);
        log.debug("[requestAccess] Requesting access: fn={}, cacheKey={}", ResourceUtils.computeResourceMetaKey(function), cacheKey);
        try {
            var access = podAccessCache.get(cacheKey, () -> CountedPodAccess.builder()
                    .podAccess(podPoolConnectorFactory.get(podPool).requestAccess(function, trailingPath))
                    .maxUsageCount(podPool.getSpec().getRunPerPod())
                    .usageCount(new AtomicInteger(0))
                    .functionPodManager(this)
                    .build());
            access.getUsageCount().incrementAndGet();
            log.debug("[requestAccess] Access acquired: pod={}, usageCount={}, maxUsageCount={}", ResourceUtils.computeResourceMetaKey(access.getPodAccess().getSelectedPod()), access.getUsageCount(), access.getMaxUsageCount());
            return access;
        } catch (ExecutionException e) {
            if (Objects.nonNull(e.getCause()) && FunctionPodAccessException.class.isAssignableFrom(e.getCause().getClass())) {
                throw (FunctionPodAccessException) e.getCause();
            }
            throw new FunctionPodAccessException("Failed to request access to pod function " + function.getMetadata().getName(), podPool, function);
        }
    }

    @Override
    public synchronized void disposeAccess(CountedPodAccess countedPodAccess) throws FunctionPodDisposalException {
        int count = countedPodAccess.getUsageCount().intValue();
        int limit = countedPodAccess.getMaxUsageCount();
        if (count<limit) {
            return;
        }
        var access = countedPodAccess.getPodAccess();
        var podKey = ResourceUtils.computeResourceMetaKey(access.getSelectedPod());
        log.info("PodAccess is being evicted: {}", countedPodAccess);
        podAccessCache.invalidate(cacheKey(access));
        var connector = podPoolConnectorFactory.get(access.getNamespace(), access.getPodPoolName());
        connector.disposeAccess(access);
        log.info("Pod is evicted: {}", podKey);
    }
}
