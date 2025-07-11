package ai.tuna.fusion.kubernetes.operator.driver;

import ai.tuna.fusion.kubernetes.operator.driver.alibabacloudfc.AlibabaCloudFunctionComputeProvisioningDriver;
import ai.tuna.fusion.kubernetes.operator.driver.awslambda.AwsLambdaProvisioningDriver;
import ai.tuna.fusion.kubernetes.operator.driver.podpool.PodPoolProvisionDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author robinqu
 */
@Configuration
public class DriversAutoConfiguration {

    @Bean
    public ProvisioningDriverRegistry driverRegistry() {
        return new StaticDriverRegistry(
                new PodPoolProvisionDriver(),
                new AwsLambdaProvisioningDriver(),
                new AlibabaCloudFunctionComputeProvisioningDriver()
        );
    }

}
