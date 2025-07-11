package ai.tuna.fusion.metadata.crd;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.kubernetes.api.model.PodSpec;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentEnvironmentSpec {
    public enum DriverType {
        PodPooling,
        AwsLambda,
        AlibabaCloudFunctionCompute
    }

    @Data
    public static class BuildRecipe {
        @Required
        private String buildScript;
        @Required
        private String builderImage;
        private String serviceAccountName;
    }

    @Data
    public static class Endpoint {
        @Required
        String protocol = "https";
        @Required
        String externalHost;
    }

    @Required
    private BuildRecipe buildRecipe;

    @Required
    private DriverType driverType = DriverType.PodPooling;

    @Data
    public static class DriverSpec {
        @Data
        public static class PodPoolSpec {
            private PodSpec podSpec;
            private int poolSize = 3;
            private String archivePvcCapacity = "10Gi";
        }

        @Data
        public static class AWSLambdaSpec {}

        @Data
        static class AlibabaCloudFunctionComputeSpec {}

        private PodPoolSpec podPoolSpec;
        private AWSLambdaSpec awsLambdaSpec;
        private AlibabaCloudFunctionComputeSpec alibabaCloudFunctionComputeSpec;
    }

    @Required
    @ValidationRule(value = "has(driver.podPooling) || has(driver.awsLambda) || has(driver.alibabaCloudFunctionCompute)", message = "driver must be included in resource definition.")
    private DriverSpec driver;

    @Required
    private Endpoint endpoint;
}
