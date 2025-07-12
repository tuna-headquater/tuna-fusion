package ai.tuna.fusion.metadata.crd.agent;

import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentDeploymentStatus {
    @Data
    public static class FunctionInfo {
        private String functionName;
    }

    @Data
    public static class LambdaInfo {
        private String functionName;
    }

    @Data
    public static class FcInfo {
        private String functionName;
    }

    private FunctionInfo function;
    private LambdaInfo lambda;
    private FcInfo fc;
    private AgentEnvironmentSpec.DriverType driverType;



}
