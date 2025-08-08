package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodAccessException;
import ai.tuna.fusion.executor.driver.podpool.FunctionPodDisposalException;
import ai.tuna.fusion.executor.driver.podpool.PodAccess;
import ai.tuna.fusion.executor.driver.podpool.PodPoolConnector;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
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
import org.apache.commons.lang3.Strings;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.*;

/**
 * @author robinqu
 */
@Slf4j
public class ApiServerPodPoolConnectorImpl implements PodPoolConnector, ResourceEventHandler<Pod> {
    private final PodPoolResources podPoolResources;
    private final BlockingQueue<String> queue;
    private final PodPool podPool;
    private final RestClient restClient;

    public ApiServerPodPoolConnectorImpl(PodPoolResources podPoolResources, PodPool podPool) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.podPoolResources = podPoolResources;
        this.podPool = podPool;
        this.queue = new LinkedBlockingQueue<>();
        podPoolResources.pod().addEventHandler(this);
    }

    @Override
    public void disposeAccess(PodAccess podAccess) throws FunctionPodDisposalException {
        var pod = podAccess.getSelectedPod();
        log.info("[disposeAccess] Evict specialized POD: {}", ResourceUtils.computeResourceMetaKey(pod));
        ResourceUtils.deleteResource(podPoolResources.getKubernetesClient(), podAccess.getNamespace(), pod.getMetadata().getName(), Pod.class, 3);
    }

    @Override
    public PodAccess requestAccess(PodFunction function) throws FunctionPodAccessException {
        log.debug("[requestAccess] fn={}", ResourceUtils.computeResourceMetaKey(function));
        var effectiveBuild = Optional.ofNullable(function.getStatus())
                .map(PodFunctionStatus::getEffectiveBuild)
                .flatMap(buildInfo -> podPoolResources.queryPodFunctionBuild(podPool.getMetadata().getNamespace(), buildInfo.getName()))
                .orElseThrow(() -> new FunctionPodAccessException("Cannot find effectiveBuild", podPool, function));

        String patch = String.format(
                "[{\"op\": \"test\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                        "{\"op\": \"add\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                        "{\"op\": \"add\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                        "{\"op\": \"add\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                        "{\"op\": \"remove\", \"path\": \"/metadata/labels/%s\"}]",
                encodeJsonPointer(GENERIC_POD_LABEL_NAME), "true",
                encodeJsonPointer(SPECIALIZED_POD_LABEL_VALUE), "true",
                encodeJsonPointer(SPECIALIZED_POD_FUNCTION_NAME_LABEL_VALUE), function.getMetadata().getName(),
                encodeJsonPointer(SPECIALIZED_POD_FUNCTION_BUILD_ID_LABEL_VALUE), effectiveBuild.getMetadata().getUid(),
                encodeJsonPointer(GENERIC_POD_LABEL_NAME));
        var pod = poll(Duration.ofSeconds(5), patch)
                .orElseThrow(()-> new FunctionPodAccessException("Cannot find available Generic Pod", podPool, function));

        var deployArchive = Optional.ofNullable(effectiveBuild.getStatus())
                .map(PodFunctionBuildStatus::getDeployArchive)
                .orElseThrow(()-> FunctionPodAccessException.of(podPool, function, "No effective build found for pod function " + function.getMetadata().getName()));
        var request = PodSpecializeRequest.builder()
                .deployArchive(deployArchive)
                .entrypoint(function.getSpec().getEntrypoint())
                .appType(function.getSpec().getAppType())
                .build();
        var responseSpec = restClient.post()
                .uri(ResourceUtils.getPodUri(pod, "/specialize"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        var response = responseSpec.toEntity(String.class);
        if (response.getStatusCode() != HttpStatusCode.valueOf(200)) {
            log.debug("[requestAccess] Non-200 response with specialization request: {}", response.getBody());
            throw FunctionPodAccessException.of(podPool, function, "Failed to specialize pod function " + function.getMetadata().getName());
        }
        log.debug("[requestAccess] Access acquired: fn={}, pod={}", ResourceUtils.computeResourceMetaKey(function), ResourceUtils.computeResourceMetaKey(pod));
        return PodAccess.builder()
                .selectedPod(pod)
                .uri(ResourceUtils.getPodUri(pod))
                .functionBuildName(effectiveBuild.getMetadata().getName())
                .functionBuildUid(effectiveBuild.getMetadata().getUid())
                .functionName(function.getMetadata().getName())
                .podPoolName(podPool.getMetadata().getName())
                .namespace(pod.getMetadata().getNamespace())
                .build();
    }

    public Optional<Pod> poll(Duration timeout, String podPatch) {
        int retries = 0;
        int MAX_POLL_RETRIES = 3;
        while (retries++ < MAX_POLL_RETRIES) {
            try {
                var podKey = queue.poll(timeout.getSeconds(), TimeUnit.SECONDS);
                Preconditions.checkNotNull(podKey, "should have polled valid podKey");
                log.debug("[poll] podKey={}", podKey);
                var parsed = ResourceUtils.parseResourceMetaKey(podKey);
                try {
                    var selectedPod = podPoolResources.queryPod(parsed.getLeft(), parsed.getRight())
                            .orElseThrow(()-> new IllegalStateException("Cannot find Pod in informer cache"));
                    return Optional.ofNullable(podPoolResources.getKubernetesClient().resource(selectedPod)
                            .inNamespace(podPool.getMetadata().getNamespace())
                            .patch(PatchContext.of(PatchType.JSON), podPatch));
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
        return Strings.CS.equals(pod.getMetadata().getLabels().get(PodPool.POD_POOL_NAME_LABEL_NAME), podPool.getMetadata().getName());
    }

    private boolean isReadyPod(Pod pod) {
        return pod.getStatus().getConditions().stream()
                .anyMatch(condition -> Strings.CS.equals(condition.getType(), "Ready") && Strings.CS.equals(condition.getStatus(), "True"));
    }

    private boolean shouldAddToQueue(Pod pod) {
        return isManagedPod(pod) && isReadyPod(pod);
    }

    private boolean existInQueue(Pod pod) {
        return queue.contains(ResourceUtils.computeResourceMetaKey(pod));
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
        var oldPodKey = ResourceUtils.computeResourceMetaKey(newObj);
        var newPodKey = ResourceUtils.computeResourceMetaKey(newObj);
        if (!shouldAddToQueue(oldObj)) {
            if (queue.remove(oldPodKey)) {
                log.info("[onUpdate] Removed GenericPod for PodPool {}: {}", podPoolKey, oldPodKey);
            }
        }
        if (shouldAddToQueue(newObj) && !existInQueue(newObj)) {
            if (queue.add(newPodKey)) {
                log.info("[onUpdate] Added GenericPod for PodPool {}: {}, ", podPoolKey, newPodKey);
            }
        }
    }

    @Override
    public void onDelete(Pod obj, boolean deletedFinalStateUnknown) {
        var podPoolKey = ResourceUtils.computeResourceMetaKey(podPool);
        var podKey = ResourceUtils.computeResourceMetaKey(obj);
        if (queue.remove(podKey)) {
            log.info("[onDelete] Removed GenericPod for PodPool {}: {}", podPoolKey, podKey);
        }
    }
}
