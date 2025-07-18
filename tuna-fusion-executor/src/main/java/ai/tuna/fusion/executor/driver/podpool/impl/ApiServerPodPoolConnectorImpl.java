package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.executor.driver.podpool.*;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.GENERIC_POD_LABEL_NAME;
import static ai.tuna.fusion.metadata.crd.podpool.PodPool.SPECIALIZED_POD_LABEL_VALUE;

/**
 * @author robinqu
 */
@Slf4j
public class ApiServerPodPoolConnectorImpl implements PodPoolConnector, ResourceEventHandler<Pod> {
    private final PodPoolResources podPoolResources;
    private final BlockingQueue<String> queue;
    private final PodPool podPool;
    private final WebClient webClient;

    public ApiServerPodPoolConnectorImpl(PodPoolResources podPoolResources, PodPool podPool) {
        this.webClient = WebClient.create();
        this.podPoolResources = podPoolResources;
        this.podPool = podPool;
        this.queue = new LinkedBlockingQueue<>();
        podPoolResources.pod().addEventHandler(this);
    }

    @Override
    public void disposeAccess(PodAccess podAccess) throws FunctionPodDisposalException {
        var pod = podAccess.getSelcetedPod();
        log.info("Evict specialized POD: {}", ResourceUtils.computeResourceMetaKey(pod));
        var result = ResourceUtils.deleteResource(podPoolResources.getKubernetesClient(), podAccess.getNamespace(), pod.getMetadata().getName(), Pod.class);
        if (!result) {
            throw FunctionPodDisposalException.of(pod, "Failed to dispose pod " + pod.getMetadata().getName());
        }
    }

    @Override
    public PodAccess requestAccess(PodFunction function, String trailingPath) throws FunctionSpecilizationException {
        var effectiveBuild = Optional.ofNullable(function.getStatus())
                .map(PodFunctionStatus::getEffectiveBuild)
                .orElseThrow(() -> new FunctionSpecilizationException("Cannot find effectiveBuild", podPool, function));

        var pod = poll(Duration.ofSeconds(5))
                .orElseThrow(()-> new FunctionSpecilizationException("Cannot find available Generic Pod", podPool, function));

        var deployArchivePath = Optional.ofNullable(effectiveBuild.getStatus())
                .map(PodFunctionBuildStatus::getDeployArchive)
                .map(PodFunctionBuildStatus.DeployArchive::getFilesystemFolderSource)
                .map(PodFunction.FilesystemFolderSource::getPath)
                .orElseThrow(()-> FunctionSpecilizationException.of(podPool, function, "No effective build found for pod function " + function.getMetadata().getName()));
        var request = PodSpecializeRequest.builder()
                .filepath(deployArchivePath)
                .functionName(function.getSpec().getEntrypoint())
                .appType(function.getSpec().getAppType())
                .build();
        var headlessService = podPoolResources.queryPodPoolService(podPool.getMetadata().getNamespace(), podPool.getMetadata().getName()).orElseThrow();
        var responseSpec = webClient.post()
                .uri(ResourceUtils.getPodUri(pod, headlessService, "/specialize"))
                .bodyValue(request)
                .retrieve();
        var response = responseSpec.toEntity(String.class)
                .block(Duration.ofSeconds(5));
        if (response == null || response.getStatusCode() != HttpStatusCode.valueOf(200)) {
            throw FunctionSpecilizationException.of(podPool, function, "Failed to specialize pod function " + function.getMetadata().getName());
        }
        return PodAccess.builder()
                .uri(ResourceUtils.getPodUri(pod, headlessService, trailingPath))
                .functionBuildName(effectiveBuild.getName())
                .functionBuildUid(effectiveBuild.getUid())
                .functionName(function.getMetadata().getName())
                .podPoolName(podPool.getMetadata().getName())
                .build();
    }

    public Optional<Pod> poll(Duration timeout) {
        int retries = 0;
        int MAX_POLL_RETRIES = 3;
        while (retries++ < MAX_POLL_RETRIES) {
            try {
                var podKey = queue.poll(timeout.getSeconds(), TimeUnit.SECONDS);
                Preconditions.checkNotNull(podKey, "should have polled valid podKey");
                log.debug("Polled podKey: {}", podKey);
                var parsed = ResourceUtils.parseResourceMetaKey(podKey);
                String patch = String.format(
                        "[{\"op\": \"test\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                                "{\"op\": \"add\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                                "{\"op\": \"remove\", \"path\": \"/metadata/labels/%s\"}]",
                        encodeJsonPointer(GENERIC_POD_LABEL_NAME), "true",
                        encodeJsonPointer(SPECIALIZED_POD_LABEL_VALUE), "true",
                        encodeJsonPointer(GENERIC_POD_LABEL_NAME));

                try {
                    var selectedPod = podPoolResources.queryPod(parsed.getLeft(), parsed.getRight())
                            .orElseThrow(()-> new IllegalStateException("Cannot find Pod in informer cache"));
                    return Optional.ofNullable(podPoolResources.getKubernetesClient().resource(selectedPod)
                            .inNamespace(podPool.getMetadata().getNamespace())
                            .patch(PatchContext.of(PatchType.JSON), patch));
                } catch (KubernetesClientException e) {
                    if (e.getCode() == 422) {
                        log.warn("Update conflict for patching pod. Retries {} of {}.", retries, MAX_POLL_RETRIES, e);
                    }
                    log.error("Unhandled K8S API error. Retries {} of {}.", retries, MAX_POLL_RETRIES, e);
                } catch (Exception e) {
                    log.error("Uncaught exception. Retries {} of {}.", retries, MAX_POLL_RETRIES, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.empty();
    }

    private static String encodeJsonPointer(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private boolean isManagedPod(Pod pod) {
        return pod.getMetadata().getLabels().containsKey(PodPool.GENERIC_POD_LABEL_NAME) && StringUtils.equals(pod.getMetadata().getLabels().get(PodPool.POD_POOL_NAME_LABEL_NAME), podPool.getMetadata().getName());
    }

    private boolean isReadyPod(Pod pod) {
        return pod.getStatus().getConditions().stream()
                .anyMatch(condition -> StringUtils.equals(condition.getType(), "Ready") && StringUtils.equals(condition.getStatus(), "True"));
    }

    private boolean shouldAddToQueue(Pod pod) {
        return isManagedPod(pod) && isReadyPod(pod);
    }

    @Override
    public void onAdd(Pod obj) {
        if (shouldAddToQueue(obj)) {
            var podKey = ResourceUtils.computeResourceMetaKey(obj);
            if (queue.add(podKey)) {
                log.info("[onAdd] Generic pod is added to PodPool {}: {}",
                        ResourceUtils.computeResourceMetaKey(podPool),
                        podKey
                );
            }
        }
    }

    @Override
    public void onUpdate(Pod oldObj, Pod newObj) {
        var podPoolKey = ResourceUtils.computeResourceMetaKey(podPool);
        if (!shouldAddToQueue(oldObj)) {
            var podKey = ResourceUtils.computeResourceMetaKey(newObj);
            if (queue.remove(podKey)) {
                log.info("[onUpdate] Removed GenericPod for PodPool {}: {}", podPoolKey, podKey);
            }
        }
        if (shouldAddToQueue(newObj) ) {
            var podKey = ResourceUtils.computeResourceMetaKey(newObj);
            if (queue.add(podKey)) {
                log.info("[onUpdate] Added GenericPod for PodPool {}: {}", podPoolKey, podKey);
            }
        }
    }

    @Override
    public void onDelete(Pod obj, boolean deletedFinalStateUnknown) {
        if (shouldAddToQueue(obj)) {
            var podPoolKey = ResourceUtils.computeResourceMetaKey(podPool);
            var podKey = ResourceUtils.computeResourceMetaKey(obj);
            if (queue.remove(podKey)) {
                log.info("[onDelete] Removed GenericPod for PodPool {}: {}", podPoolKey, podKey);
            }
        }
    }
}
