package ai.tuna.fusion.metadata.crd;

import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.*;

/**
 * @author robinqu
 */
@Slf4j
public class PodPoolResourceUtils {
    public static String computePodPoolDeploymentName(PodPool resource) {
        return resource.getMetadata().getName() + "-deploy";
    }

    public static Map<String, String> computeGenericPodSelectors(PodPool resource) {
        return Map.of(
                GENERIC_POD_LABEL_NAME, "true",
                POD_POOL_NAME_LABEL_NAME, resource.getMetadata().getName(),
                DR_SELECTOR, "true"
        );
    }

    public static List<Pod> listSpecializedPods(PodPool resource, KubernetesClient kubernetesClient) {
        return kubernetesClient.resources(Pod.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withLabels(computeSpecializedPodLabels(resource))
                .list()
                .getItems();
    }

    public static Map<String, String> computeSpecializedPodLabels(PodPool podPool) {
        return Map.of(
                SPECIALIZED_POD_LABEL_VALUE, "true",
                POD_POOL_NAME_LABEL_NAME, podPool.getMetadata().getName(),
                DR_SELECTOR, "true"
        );
    }

    public static Map<String, String> computeDeployLabels(PodPool resource) {
        return Map.of(
                POD_POOL_NAME_LABEL_NAME, resource.getMetadata().getName(),
                DR_SELECTOR, "true"
        );
    }

    public static Map<String, String> computeServiceLabels(PodPool resource) {
        return computeDeployLabels(resource);
    }

    public static Optional<Deployment> getPodPoolDeployment(PodPool resource, KubernetesClient kubernetesClient) {
        return Optional.ofNullable(kubernetesClient.resources(Deployment.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(computePodPoolDeploymentName(resource))
                .get());
    }

    public static Optional<PodPool> getPodPoolForAgentEnvironment(AgentEnvironment resource, KubernetesClient kubernetesClient) {
        return Optional.ofNullable(kubernetesClient.resources(PodPool.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .get());
    }

    public static Optional<PodFunction> getReferencedPodFunction(PodFunctionBuild resource, KubernetesClient kubernetesClient) {
        return Optional.ofNullable(kubernetesClient.resources(PodFunction.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getSpec().getPodFunctionName())
                .get());
    }

    public static Optional<PodPool> getReferencedPodPool(PodFunction resource, KubernetesClient kubernetesClient) {
        return Optional.ofNullable(
                kubernetesClient.resources(PodPool.class)
                        .inNamespace(resource.getMetadata().getNamespace())
                        .withName(resource.getSpec().getPodPoolName())
                        .get()
        );
    }

    public static Optional<String> getReferencedPodFunctionName(PodFunctionBuild build) {
        return AgentResourceUtils.getReferencedResourceName(build, PodFunction.class);
    }

    public static String computeJobName(PodFunctionBuild build) {
        return build.getMetadata().getName() + "-job";
    }

    public static boolean isJobTerminalPhase(String phase) {
        return Strings.CS.equals("Succeeded", phase) || Strings.CS.equals("Failed", phase);
    }

    public static Optional<String> getPodLog(KubernetesClient client, String ns, PodFunctionBuildStatus.JobPodInfo info) {
        var states = List.of("Running", "Succeeded", "Failed");
        if (states.stream().anyMatch(state -> Strings.CS.equals(state, info.getPodPhase()))) {
            try {
                var log = getPodLog(client, ns, info.getPodName());
                return Optional.ofNullable(log);
            } catch (Exception e) {
                log.error("Failed to fetch log for pod {}", info.getPodName(), e);
            }
        }
        return Optional.empty();
    }

    public static String getPodLog(KubernetesClient client, String ns, String podName) {
        return client.pods().inNamespace(ns)
                .withName(podName)
                .getLog(true);
    }

    public static String computePodPoolServiceName(PodPool res
    ) {
        return res.getMetadata().getName() + "-service";
    }

    public static String computeDeployArchivePath(PodFunctionBuild resource) {
        return PodFunctionBuild.ARCHIVE_ROOT_PATH.resolve("deployments").resolve(resource.getMetadata().getUid()).toString();
    }

    public static String computeDeployFileAssetPath(String buildUid, PodFunction.FileAsset fileAsset) {
        return PodFunctionBuild.ARCHIVE_ROOT_PATH.resolve("deployments").resolve(buildUid).resolve(fileAsset.getFileName()).toString();
    }

    public static String computeSourceArchivePath(PodFunctionBuild resource) {
        return PodFunctionBuild.ARCHIVE_ROOT_PATH.resolve("sources").resolve(resource.getMetadata().getUid()).toString();
    }

}
