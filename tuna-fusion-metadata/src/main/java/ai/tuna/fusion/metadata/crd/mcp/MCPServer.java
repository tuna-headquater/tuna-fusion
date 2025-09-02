package ai.tuna.fusion.metadata.crd.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * @author robinqu
 */
@Group("fusion.tuna.ai")
@Version("v1")
@ShortNames({"mcp"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class MCPServer extends CustomResource<MCPServerSpec, MCPServerStatus> {

}
