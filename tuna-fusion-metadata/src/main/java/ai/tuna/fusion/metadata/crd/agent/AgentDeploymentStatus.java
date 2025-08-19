package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentDeploymentStatus {
    @Data
    public static class PodFunctionInfo {
        private String functionName;
        private PodFunctionStatus status;
    }

    @Data
    public static class LambdaInfo {
        private String functionName;
    }

    @Data
    public static class FcInfo {
        private String functionName;
    }

    private PodFunctionInfo function;
    private LambdaInfo lambda;
    private FcInfo fc;
    private AgentEnvironmentSpec.DriverType driverType;
    private String executorUrl;
}
