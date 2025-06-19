package ai.tuna.fusion.kubernetes.operator.crd;

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
