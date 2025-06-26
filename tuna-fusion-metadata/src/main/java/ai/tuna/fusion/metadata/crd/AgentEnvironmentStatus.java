package ai.tuna.fusion.metadata.crd;

import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentStatus {

    @Data
    public static class FissionEnvStatus {
        String name;
    }

    private FissionEnvStatus fissionEnv;

}
