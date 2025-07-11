package ai.tuna.fusion.kubernetes.operator.driver.alibabacloudfc;

import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriver;
import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;

/**
 * @author robinqu
 */
public class AlibabaCloudFunctionComputeProvisioningDriver implements ProvisioningDriver {
    @Override
    public Provisioner<AgentDeployment> agentDeployment() {
        return null;
    }

    @Override
    public Provisioner<AgentBuild> agentBuild() {
        return null;
    }

    @Override
    public Provisioner<AgentEnvironment> agentEnvironment() {
        return null;
    }
}
