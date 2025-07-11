package ai.tuna.fusion.metadata.crd;

import lombok.Data;

import java.util.Map;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentStatus {

    @Data
    public static class PodPoolStatus {
        private String deploymentName;
        private Map<String, String> genericPodSelectors;
        private int availablePods;
        private int orphanPods;
    }

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
