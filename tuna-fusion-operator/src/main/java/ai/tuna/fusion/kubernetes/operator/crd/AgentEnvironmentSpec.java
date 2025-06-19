package ai.tuna.fusion.kubernetes.operator.crd;

import lombok.Data;

import java.util.Map;

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

    EngineType engineType = EngineType.Fission;
    FissionEnvOptions fissionEnv;

}
