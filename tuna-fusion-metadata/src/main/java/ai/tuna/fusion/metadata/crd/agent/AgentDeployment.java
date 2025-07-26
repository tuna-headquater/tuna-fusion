package ai.tuna.fusion.metadata.crd.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        @AdditionalPrinterColumn(name = "Env", jsonPath = ".spec.environmentName"),
        @AdditionalPrinterColumn(name = "URL", jsonPath = ".status.executorUrl")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentDeployment extends CustomResource<AgentDeploymentSpec, AgentDeploymentStatus> implements Namespaced {


}
