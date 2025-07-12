package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.crd.podpool.PodPoolStatus;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentStatus {

    @Data
    public static class AwsLambdaStatus {

    }

    @Data
    public static class AlibabaCloudFCStatus {

    }

    private PodPoolStatus podPool;
    private AwsLambdaStatus awsLambda;
    private AlibabaCloudFCStatus fc;

}
