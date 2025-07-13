package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.*;

/**
 * @author robinqu
 */
@Slf4j
@Component
public class FunctionPodResolverImpl implements FunctionPodManager {

    private static final long RESYNC_PERIOD = 10 * 60 * 1000;

    private final WebClient webClient;
    private final KubernetesClient kubernetesClient;
    private final PodPoolResources podPoolResources;


    public FunctionPodResolverImpl(KubernetesClient kubernetesClient, PodPoolResources podPoolResources) {
        this.podPoolResources = podPoolResources;
        this.webClient = WebClient.create();
        this.kubernetesClient = kubernetesClient;

    }

    @Override
    public Pod specializePod(PodFunction function, PodPool podPool) throws FunctionPodOperationException {
        var pod = selectPod(function, podPool);
        var deployArchivePath = Optional.ofNullable(function.getStatus())
                .map(PodFunctionStatus::getEffectiveBuild)
                .map(PodFunctionStatus.BuildInfo::getStatus)
                .map(PodFunctionBuildStatus::getDeployArchivePath).orElseThrow(()-> FunctionPodOperationException.of(podPool, function, "No effective build found for pod function " + function.getMetadata().getName()));
        var request = PodSpecializeRequest.builder()
                .filepath(deployArchivePath)
                .functionName(function.getSpec().getEntrypoint())
                .build();
        var responseSpec = webClient.post()
                .uri("/specialize")
                .bodyValue(request)
                .retrieve();
        var response = responseSpec.toEntity(String.class)
                .block(Duration.ofSeconds(5));
        if (response == null || response.getStatusCode() != HttpStatusCode.valueOf(200)) {
            throw FunctionPodOperationException.of(podPool, function, "Failed to specialize pod function " + function.getMetadata().getName());
        }
        return pod;
    }

    private Pod selectPod(PodFunction function, PodPool podPool) throws FunctionPodOperationException {
        var poolKey = getPodPoolKey(podPool);
        var genericPods = podPoolResources.queryGenericPods(podPool.getMetadata().getNamespace(), podPool.getMetadata().getName());
        while (!genericPods.isEmpty()) {
            var selectedPod = genericPods.getFirst();
            String patch = String.format(
                    "[{\"op\": \"test\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                            "{\"op\": \"add\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}," +
                            "{\"op\": \"remove\", \"path\": \"/metadata/labels/%s\"}]",
                    encodeJsonPointer(GENERIC_POD_LABEL_NAME), "true",
                    encodeJsonPointer(SPECIALIZED_POD_LABEL_VALUE), "true",
                    encodeJsonPointer(GENERIC_POD_LABEL_NAME));
            try {
                return kubernetesClient.resource(selectedPod)
                        .inNamespace(podPool.getMetadata().getNamespace())
                        .patch(PatchContext.of(PatchType.JSON), patch);
            } catch (KubernetesClientException e) {
                if (e.getCode() == 422) {
                    log.warn("Update conflicts for pod {}", selectedPod.getMetadata().getName());
                    genericPods.remove(selectedPod);
                }
            }
        }
        throw FunctionPodOperationException.of(podPool, function, "No pod found for pod pool " + poolKey);
    }


    @Override
    public void disposePod(Pod pod) throws FunctionPodDisposalException {
        var result = ResourceUtils.deleteResource(kubernetesClient, pod.getMetadata().getNamespace(), pod.getMetadata().getName(), Pod.class);
        if (!result) {
            throw FunctionPodDisposalException.of(pod, "Failed to dispose pod " + pod.getMetadata().getName());
        }
    }

    private static String encodeJsonPointer(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private String getPodPoolKey(PodPool podPool) {
        return podPool.getMetadata().getNamespace() + "/" + podPool.getMetadata().getName();
    }

    private Optional<String> computePodPoolKey(Pod pod) {
        return Optional.ofNullable(pod.getMetadata())
                .map(ObjectMeta::getLabels)
                .map(labels -> labels.get(MANAGED_POD_POOL_LABEL_NAME))
                .filter(StringUtils::isNoneBlank)
                .map(podPoolName -> pod.getMetadata().getNamespace() + "/" + podPoolName);
    }

}
