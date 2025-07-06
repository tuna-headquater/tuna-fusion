package ai.tuna.fusion.metadata.crd;

import io.fabric8.generator.annotation.Required;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentSpec {
    public enum EngineType {
        Fission
    }
    @Data
    public static class FissionEnvOptions {
        @Required
        String runtimeImage;
        @Required
        String builderImage;
        int poolSize = 3;
    }
    @Data
    public static class EngineOptions {
        @Required
        private FissionEnvOptions fission;
    }

    @Data
    public static class BuildRecipe {
        @Required
        private String buildScript;
        @Required
        private String builderImage;
        private String serviceAccountName;
    }

    @Data
    public static class Endpoint {
        @Required
        String protocol = "https";
        @Required
        String host;
    }

    @Required
    private BuildRecipe buildRecipe;

    @Required
    private EngineType engineType = EngineType.Fission;

    @Required
    private EngineOptions engineOptions;

    @Required
    private Endpoint endpoint;
}
