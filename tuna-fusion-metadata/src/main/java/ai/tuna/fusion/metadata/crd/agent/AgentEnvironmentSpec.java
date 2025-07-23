package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.crd.podpool.PodPoolSpec;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentSpec {
    public enum DriverType {
        PodPool,
        AwsLambda,
        AlibabaCloudFunctionCompute
    }

    @Data
    public static class DriverSpec {
        @Required
        private DriverType type = DriverType.PodPool;

        @Data
        public static class AWSLambdaSpec {}

        @Data
        static class AlibabaCloudFunctionComputeSpec {}

        private PodPoolSpec podPoolSpec;
        private AWSLambdaSpec awsLambdaSpec;
        private AlibabaCloudFunctionComputeSpec alibabaCloudFunctionComputeSpec;
    }

    @Required
    @ValidationRule(value = "has(self.podPoolSpec) || has(self.awsLambdaSpec) || has(self.alibabaCloudFunctionComputeSpec)", message = "driver must be included in resource definition.")
    private DriverSpec driver;

    @Data
    public static class Executor {
        @Required
        private String baseUrl;
    }
    @Required
    private Executor executor;


}
