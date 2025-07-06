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

    @Data
    public static class A2ARuntimeStatus {
        private String initLogs;
        private String teardownLogs;
    }

    private FissionEnvStatus fissionEnv;
    private A2ARuntimeStatus a2a;

}
