package ai.tuna.fusion.metadata.crd;

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumns;
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
@ShortNames({"ad"})
@AdditionalPrinterColumns({
        @AdditionalPrinterColumn(name = "Catalogue", jsonPath = ".metadata.ownerReferences[0].name"),
        @AdditionalPrinterColumn(name = "Env", jsonPath = ".spec.environmentName"),
        @AdditionalPrinterColumn(name = "Build", jsonPath = ".spec.currentBuild.name")
})
public class AgentDeployment extends CustomResource<AgentDeploymentSpec, AgentDeploymentStatus> implements Namespaced {


}
