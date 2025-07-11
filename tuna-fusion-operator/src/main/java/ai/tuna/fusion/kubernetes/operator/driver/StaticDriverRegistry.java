package ai.tuna.fusion.kubernetes.operator.driver;

import ai.tuna.fusion.kubernetes.operator.driver.alibabacloudfc.AlibabaCloudFunctionComputeProvisioningDriver;
import ai.tuna.fusion.kubernetes.operator.driver.awslambda.AwsLambdaProvisioningDriver;
import ai.tuna.fusion.kubernetes.operator.driver.podpool.PodPoolProvisionDriver;
import ai.tuna.fusion.metadata.crd.AgentEnvironmentSpec;

import java.util.Map;

/**
 * @author robinqu
 */
public class StaticDriverRegistry implements ProvisioningDriverRegistry {


    private final Map<AgentEnvironmentSpec.DriverType, ProvisioningDriver> drivers;

    public StaticDriverRegistry(PodPoolProvisionDriver podPool, AwsLambdaProvisioningDriver awsLambda, AlibabaCloudFunctionComputeProvisioningDriver alibabaCloudFunctionCompute) {
        this.drivers = Map.of(
                AgentEnvironmentSpec.DriverType.PodPooling, podPool,
                AgentEnvironmentSpec.DriverType.AwsLambda, awsLambda,
                AgentEnvironmentSpec.DriverType.AlibabaCloudFunctionCompute, alibabaCloudFunctionCompute
        );
    }

    @Override
    public ProvisioningDriver getDriver(AgentEnvironmentSpec.DriverType driverType) {
        return drivers.get(driverType);
    }
}
