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

//    @Data
//    public static class BuildRecipe {
//        @Required
//        private String buildScript;
//        @Required
//        private String builderImage;
//        private String serviceAccountName;
//    }

    @Data
    public static class Endpoint {
        @Required
        String protocol = "https";
        @Required
        String externalHost;
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
    @ValidationRule(value = "has(driver.podPoolSpec) || has(driver.awsLambdaSpec) || has(driver.alibabaCloudFunctionComputeSpec)", message = "driver must be included in resource definition.")
    private DriverSpec driver;

    @Required
    private Endpoint endpoint;
}
