package ai.tuna.fusion.kubernetes.operator.driver;

import ai.tuna.fusion.metadata.crd.AgentEnvironmentSpec;

import java.util.Collection;

/**
 * @author robinqu
 */
public interface ProvisioningDriverRegistry {
    ProvisioningDriver getDriver(AgentEnvironmentSpec.DriverType driverType);

    Collection<ProvisioningDriver> allDrivers();
}
