package ai.tuna.fusion.kubernetes.operator.podpool;

import ai.tuna.fusion.kubernetes.operator.agent.AgentResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.*;

/**
 * @author robinqu
 */
public class PodPoolResourceUtils {

    public static String getPodPoolDeploymentName(PodPool resource) {
        return resource.getMetadata().getName() + "-deploy";
    }


    public static Map<String, String> computeGenericPodSelectors(PodPool resource) {
        return Map.of(
                GENERIC_POD_LABEL_NAME, "true",
                MANAGED_POD_POOL_LABEL_NAME, resource.getMetadata().getName()
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
                MANAGED_POD_POOL_LABEL_NAME, podPool.getMetadata().getName()
        );
    }

    public static Map<String, String> computeDeployLabels(PodPool resource) {
        return Map.of(
                MANAGED_POD_POOL_LABEL_NAME, resource.getMetadata().getName()
        );
    }

    public static Map<String, String> computeServiceLabels(PodPool resource) {
        return computeDeployLabels(resource);
    }

    public static Optional<Deployment> getPodPoolDeployment(PodPool resource, KubernetesClient kubernetesClient) {
        return Optional.ofNullable(kubernetesClient.resources(Deployment.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(getPodPoolDeploymentName(resource))
                .get());
    }

    public static Optional<PodPool> getPodPoolForAgentEnvironment(AgentEnvironment resource, KubernetesClient kubernetesClient) {
        return Optional.ofNullable(kubernetesClient.resources(PodPool.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .get());
    }

    public static Optional<PodFunction> getReferencedPodFunction(PodFunctionBuild resource, KubernetesClient kubernetesClient) {
        var ref = resource.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> StringUtils.equals(ownerReference.getKind(), HasMetadata.getKind(PodFunction.class)))
                .filter(ownerReference -> StringUtils.equals(ownerReference.getApiVersion(), HasMetadata.getApiVersion(PodFunction.class)))
                .findFirst();

        return ref.map(ownerReference -> kubernetesClient.resources(PodFunction.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(ownerReference.getName())
                .get());
    }

    public static Optional<PodPool> getReferencedPodPool(PodFunction resource, KubernetesClient kubernetesClient) {
        return AgentResourceUtils.getReferencedResource(kubernetesClient, resource, PodPool.class);
    }

    public static Optional<String> getReferencedPodFunctionName(PodFunctionBuild build) {
        return AgentResourceUtils.getReferencedResourceName(build, PodFunction.class);
    }

    public static boolean isJobTerminalPhase(String phase) {
        return StringUtils.equals("Succeeded", phase) || StringUtils.equals("Failed", phase);
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

    public static String computeDeployArchiveSubPath(PodFunctionBuild resource) {
        return "/deployments/" + resource.getMetadata().getUid();
    }

    public static String computeSourceArchiveSubPath(PodFunctionBuild resource) {
        return "/sources/" + resource.getMetadata().getUid();
    }

}
