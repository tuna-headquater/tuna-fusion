package ai.tuna.fusion.metadata.crd.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
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
@ShortNames({"ae"})
@AdditionalPrinterColumn(name = "Driver", jsonPath = ".spec.driver.type")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentEnvironment extends CustomResource<AgentEnvironmentSpec, AgentEnvironmentStatus> implements Namespaced {
}
