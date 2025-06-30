package ai.tuna.fusion.kubernetes.operator;

import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * @author robinqu
 */
public class ResourceUtils {

    public static void deleteFissionFunction(AgentDeployment agentDeployment, KubernetesClient client) {
        deleteFissionResource(computeRouteName(agentDeployment), agentDeployment.getMetadata().getNamespace(), client, "Function");
    }

    public static void deleteFissionRoute(AgentDeployment agentDeployment, KubernetesClient client) {
        deleteFissionResource(computeRouteName(agentDeployment), agentDeployment.getMetadata().getNamespace(), client, "HttpTrigger");
    }

    public static void deleteFissionResource(String name, String ns, KubernetesClient client, String kind) {
        client.genericKubernetesResources(CustomResourceDefinitionContext.fromApiResource("v1", new APIResourceBuilder().withKind(kind).withGroup("fission.io").build()))
                .inNamespace(ns)
                .withName(name)
                .delete();
    }

    public static String computeFunctionName(AgentDeployment agentDeployment) {
        return agentDeployment.getMetadata().getName();
    }

    public static String computeRouteName(AgentDeployment agentDeployment) {
        return agentDeployment.getMetadata().getName();
    }

    public static String getReferencedAgentDeploymentName(AgentBuild agentBuild) {
        return agentBuild.getMetadata().getOwnerReferences().stream()
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

    public static Optional<AgentDeployment> getReferencedAgentDeployment(final KubernetesClient client, AgentBuild resource) {
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

    public static boolean isJobTerminalPhase(String phase) {
        return StringUtils.equals("Succeeded", phase) || StringUtils.equals("Failed", phase);
    }




}
