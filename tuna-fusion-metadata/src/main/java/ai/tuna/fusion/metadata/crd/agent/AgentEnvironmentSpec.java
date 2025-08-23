package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.crd.podpool.PodPoolSpec;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @ValidationRule(value = "oldSelf.type == self.type", message = "driver.type cannot be changed after creation.")
    private DriverSpec driver;

    @Data
    public static class Executor {
        @Required
        private String baseUrl;
    }
    private Executor executor;


}
