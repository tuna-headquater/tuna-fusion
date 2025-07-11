package ai.tuna.fusion.kubernetes.operator.driver;

import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;

import java.util.List;

/**
 * Three aspects for driver:
 * 1. Environment initialization
 * 2. Build job initialization
 * 3. Deployment initialization
 *
 * @author robinqu
 */
public interface ProvisioningDriver {
    interface Provisioner<T extends HasMetadata> {
        Workflow<T> workflow();
        UpdateControl<T> statusUpdate(T primary, Context<T> context);
        List<DependentResource<?,T>> dependentResource();
    }

    Provisioner<AgentDeployment> agentDeployment();
    Provisioner<AgentBuild> agentBuild();
    Provisioner<AgentEnvironment> agentEnvironment();


}
