package ai.tuna.fusion.metadata.crd.agent;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * @author robinqu
 */
@Group("fusion.tuna.ai")
@Version("v1")
@ShortNames({"ac"})
public class AgentCatalogue extends CustomResource<AgentCatalogueSpec, AgentCatalogueStatus> implements Namespaced {
}
