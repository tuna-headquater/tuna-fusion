package ai.tuna.fusion.metadata.crd.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MCPServerSpec {
    enum Type {
        SourceDeployment,
        PackagedDeployment,
        SchemaDeployment
    }

    static class SourceDeployment {
    }

    static class PackagedDeployment {
    }

    static class SchemaDeployment {
    }

    private Type type;
    private SourceDeployment source;
    private PackagedDeployment packaged;
    private SchemaDeployment schema;

}
