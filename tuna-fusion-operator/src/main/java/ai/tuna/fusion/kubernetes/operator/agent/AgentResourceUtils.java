package ai.tuna.fusion.kubernetes.operator.agent;

import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.agent.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;
import java.util.Optional;

/**
 * @author robinqu
 */
public class AgentResourceUtils {

    public static String computeFunctionName(AgentDeployment agentDeployment) {
        return agentDeployment.getMetadata().getName() + "-function";
    }

    public static String getReferencedAgentDeploymentName(PodFunctionBuild podFunctionBuild) {
        return podFunctionBuild.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> StringUtils.equals(ownerReference.getKind(), HasMetadata.getKind(AgentDeployment.class)))
                .findFirst()
                .map(OwnerReference::getName)
                .orElseThrow();
    }

    public static String getReferenceAgentCatalogueName(AgentDeployment agentDeployment) {
        return agentDeployment.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> StringUtils.equals(ownerReference.getKind(), HasMetadata.getKind(AgentCatalogue.class)))
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


    public static <Owner extends HasMetadata, Subject extends HasMetadata> Optional<Owner> getReferencedResource(final KubernetesClient client, Subject resource, Class<Owner> ownerClass) {
        var ref = resource.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> StringUtils.equals(ownerReference.getKind(), HasMetadata.getKind(ownerClass)))
                .filter(ownerReference -> StringUtils.equals(ownerReference.getApiVersion(), HasMetadata.getApiVersion(ownerClass)))
                .findFirst();

        return ref.map(ownerReference -> client.resources(ownerClass)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(ownerReference.getName())
                .get());
    }

    public static <Owner extends HasMetadata, Subject extends HasMetadata> Optional<String> getReferencedResourceName(Subject resource, Class<Owner> ownerClass) {
        return resource.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> StringUtils.equals(ownerReference.getKind(), HasMetadata.getKind(ownerClass)))
                .filter(ownerReference -> StringUtils.equals(ownerReference.getApiVersion(), HasMetadata.getApiVersion(ownerClass)))
                .findFirst()
                .map(OwnerReference::getName);
    }


    public static String routeUrl(AgentDeployment agentDeployment) {
        var substitutor = new StringSubstitutor(Map.of(
                "namespace", agentDeployment.getMetadata().getNamespace(),
                "agentCatalogueName", AgentResourceUtils.getReferenceAgentCatalogueName(agentDeployment),
                "agentDeploymentName", agentDeployment.getMetadata().getName(),
                "agentEnvironmentName", agentDeployment.getSpec().getEnvironmentName()
        ));
        var urlTemplate = agentDeployment.getSpec().getAgentCard().getUrl();
        if (!urlTemplate.startsWith("/")) {
            urlTemplate = "/" + urlTemplate;
        }
        return substitutor.replace(urlTemplate);
    }


    private static final String AGENT_URL_TEMPLATE = "${endpointProtocol}://${endpointHost}${routeUrl}";
    public static  String agentExternalUrl(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment) {
        var endpoint = agentEnvironment.getSpec().getDriver().getPodPoolSpec().getEndpoint();
        var substitutor = new StringSubstitutor(Map.of(
                "endpointProtocol", endpoint.getProtocol(),
                "endpointHost", endpoint.getExternalHost(),
                "routeUrl", routeUrl(agentDeployment)
        ));
        return substitutor.replace(AGENT_URL_TEMPLATE);
    }




}
