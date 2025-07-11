package ai.tuna.fusion.kubernetes.operator.driver.podpool;

import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author robinqu
 */
public class PodPoolResourceUtils {

    public static String getPodPoolDeploymentName(AgentEnvironment agentEnvironment) {
        return agentEnvironment.getMetadata().getName() + "-deploy";
    }

    public static String getArchivePvcName(AgentEnvironment agentEnvironment) {
        return agentEnvironment.getMetadata().getName() + "-archive-pvc";
    }


    public static Map<String, String> computePodSelectors(AgentEnvironment agentEnvironment) {
        return Map.of(
                "is-generic-pod", "true",
                "managed-by-pod-pool", "true",
                "managed-by-agent-env", agentEnvironment.getMetadata().getName()
        );
    }

    public static List<Pod> listOrphanPods(AgentEnvironment agentEnvironment, KubernetesClient kubernetesClient) {
        return kubernetesClient.resources(Pod.class)
                .inNamespace(agentEnvironment.getMetadata().getNamespace())
                .withLabels(computeOrphanPodLabels(agentEnvironment))
                .list()
                .getItems();
    }

    public static Map<String, String> computeOrphanPodLabels(AgentEnvironment agentEnvironment) {
        return Map.of(
                "managed-by-agent-env", agentEnvironment.getMetadata().getName(),
                "is-specialized-pod", "true",
                "managed-by-pod-pool", "true"
        );
    }

    public static Map<String, String> computeDeployLabels(AgentEnvironment agentEnvironment) {
        return Map.of(
                "managed-by-agent-env", agentEnvironment.getMetadata().getName()
        );
    }

    public static Optional<Deployment> getPodPoolDeployment(AgentEnvironment agentEnvironment, KubernetesClient kubernetesClient) {
        return Optional.ofNullable(kubernetesClient.resources(Deployment.class)
                .inNamespace(agentEnvironment.getMetadata().getNamespace())
                .withName(getPodPoolDeploymentName(agentEnvironment))
                .get());
    }

}
