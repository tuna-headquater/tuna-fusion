package ai.tuna.fusion.metadata.crd;

import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentSpec {
    enum EngineType {
        Fission
    }
    @Data
    public static class FissionEnvOptions {
        String runtimeImage;
        String builderImage;
        int poolSize;
    }
    @Data
    public static class EngineOptions {
        private FissionEnvOptions fission;
    }

    @Data
    public static class BuildRecipe {
        private String buildScript;
        private String builderImage;
        private String serviceAccountName;
    }

    private BuildRecipe buildRecipe;

    private EngineType engineType = EngineType.Fission;
    private EngineOptions engineOptions;

}
