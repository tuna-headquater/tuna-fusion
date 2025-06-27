package ai.tuna.fusion.kubernetes.operator;

import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.StringUtils;

/**
 * @author robinqu
 */
public class ResourceUtils {

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

    public static AgentDeployment getReferencedAgentDeployment(final KubernetesClient client, AgentBuild resource) {
        return client.resources(AgentDeployment.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(getReferencedAgentDeploymentName(resource))
                .get();
    }




}
