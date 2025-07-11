package ai.tuna.fusion.kubernetes.operator.driver.podpool;

import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriver;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;

import java.util.List;

/**
 * @author robinqu
 */
public class PodPoolAgentDeploymentProvisioner implements ProvisioningDriver.Provisioner<AgentDeployment> {
    @Override
    public Workflow<AgentDeployment> workflow() {
        return null;
    }

    @Override
    public UpdateControl<AgentDeployment> statusUpdate(AgentDeployment primary, Context<AgentDeployment> context) {
        return null;
    }

    @Override
    public List<DependentResource<?, AgentDeployment>> dependentResource() {
        return List.of();
    }
}
