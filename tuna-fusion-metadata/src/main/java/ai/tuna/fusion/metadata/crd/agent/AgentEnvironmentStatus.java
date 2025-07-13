package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.crd.podpool.PodPoolStatus;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentStatus {

    @Data
    public static class AwsLambdaInfo {

    }

    @Data
    public static class AlibabaCloudFCInfo {

    }

    @Data
    public static class PodPoolInfo {
        private String name;
        private PodPoolStatus status;
    }

    private PodPoolInfo podPool;
    private AwsLambdaInfo awsLambda;
    private AlibabaCloudFCInfo fc;

}
