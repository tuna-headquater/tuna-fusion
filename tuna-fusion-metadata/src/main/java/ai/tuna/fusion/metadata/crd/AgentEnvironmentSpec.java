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

    private EngineType engineType = EngineType.Fission;
    private FissionEnvOptions fissionEnv;

}
