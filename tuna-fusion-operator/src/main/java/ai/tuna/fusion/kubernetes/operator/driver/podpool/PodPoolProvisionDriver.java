package ai.tuna.fusion.kubernetes.operator.driver.podpool;

import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriver;
import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import lombok.extern.slf4j.Slf4j;

/**
 * @author robinqu
 */
@Slf4j
public class PodPoolProvisionDriver implements ProvisioningDriver {


    private final PodPoolAgentEnvironmentProvisioner agentEnvironmentProvisioner;
    private final PodPoolAgentBuildProvisioner agentBuildProvisioner;
    private final PodPoolAgentDeploymentProvisioner agentDeploymentProvisioner;

    public PodPoolProvisionDriver() {
        agentEnvironmentProvisioner = new PodPoolAgentEnvironmentProvisioner();
        agentBuildProvisioner = new PodPoolAgentBuildProvisioner();
        agentDeploymentProvisioner = new PodPoolAgentDeploymentProvisioner();
    }

    @Override
    public Provisioner<AgentDeployment> agentDeployment() {
        return agentDeploymentProvisioner;
    }

    @Override
    public Provisioner<AgentBuild> agentBuild() {
        return agentBuildProvisioner;
    }

    @Override
    public Provisioner<AgentEnvironment> agentEnvironment() {
        return agentEnvironmentProvisioner;
    }
}
