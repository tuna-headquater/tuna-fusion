package ai.tuna.fusion.metadata.crd;

import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.Strings;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;
import java.util.Optional;

/**
 * @author robinqu
 */
public class AgentResourceUtils {

    public static String computePodPoolName(AgentEnvironment agentEnvironment) {
        return agentEnvironment.getMetadata().getName() + "-pod-pool";
    }

    public static String computeFunctionName(AgentDeployment agentDeployment) {
        return agentDeployment.getMetadata().getName() + "-function";
    }

    public static String getReferencedAgentDeploymentName(PodFunctionBuild podFunctionBuild) {
        return podFunctionBuild.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> Strings.CS.equals(ownerReference.getKind(), HasMetadata.getKind(AgentDeployment.class)))
                .findFirst()
                .map(OwnerReference::getName)
                .orElseThrow();
    }

    public static Optional<AgentDeployment> getReferencedAgentDeployment(final KubernetesClient client, PodFunctionBuild resource) {
        return Optional.ofNullable(client.resources(AgentDeployment.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(getReferencedAgentDeploymentName(resource))
                .get());
    }

    public static Optional<AgentEnvironment> getReferencedAgentEnvironment(final KubernetesClient client, AgentDeployment agentDeployment) {
        return Optional.ofNullable(client.resources(AgentEnvironment.class)
                .inNamespace(agentDeployment.getMetadata().getNamespace())
                .withName(agentDeployment.getSpec().getEnvironmentName())
                .get());
    }

    public static Optional<PodPool> getPodPoolForAgentEnvironment(AgentEnvironment resource, KubernetesClient kubernetesClient) {
        return ResourceUtils.getKubernetesResource(kubernetesClient,
                computePodPoolName(resource),
                resource.getMetadata().getNamespace(),
                PodPool.class
        );
    }


    public static <Owner extends HasMetadata, Subject extends HasMetadata> Optional<Owner> getReferencedResource(final KubernetesClient client, Subject resource, Class<Owner> ownerClass) {
        var ref = resource.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> Strings.CS.equals(ownerReference.getKind(), HasMetadata.getKind(ownerClass)))
                .filter(ownerReference -> Strings.CS.equals(ownerReference.getApiVersion(), HasMetadata.getApiVersion(ownerClass)))
                .findFirst();

        return ref.map(ownerReference -> client.resources(ownerClass)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(ownerReference.getName())
                .get());
    }

    public static <Owner extends HasMetadata, Subject extends HasMetadata> Optional<String> getReferencedResourceName(Subject resource, Class<Owner> ownerClass) {
        return resource.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> Strings.CS.equals(ownerReference.getKind(), HasMetadata.getKind(ownerClass)))
                .filter(ownerReference -> Strings.CS.equals(ownerReference.getApiVersion(), HasMetadata.getApiVersion(ownerClass)))
                .findFirst()
                .map(OwnerReference::getName);
    }


    private static final String AGENT_EXECUTOR_URL_TEMPLATE = "${endpointProtocol}://${endpointHost}/a2a/namespaces/${namespace}/agents/${agentDeploymentName}";
    public static  String agentExternalUrl(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment) {
        var endpoint = agentEnvironment.getSpec().getExecutor();
        var substitutor = new StringSubstitutor(Map.of(
                "endpointProtocol", endpoint.getProtocol(),
                "endpointHost", endpoint.getExternalHost(),
                "namespace", agentDeployment.getMetadata().getNamespace(),
                "agentEnvironmentName", agentEnvironment.getMetadata().getName(),
                "agentDeploymentName", agentDeployment.getMetadata().getName()
        ));
        return substitutor.replace(AGENT_EXECUTOR_URL_TEMPLATE);
    }

}
