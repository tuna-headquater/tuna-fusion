package ai.tuna.fusion.kubernetes.operator.agent.reconciler;

import ai.tuna.fusion.kubernetes.operator.agent.dr.AgentDeploymentPodFunctionDependentResource;
import ai.tuna.fusion.kubernetes.operator.podpool.dr.PodPoolDeploymentDependentResource;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * @author robinqu
 */
@Component
@Slf4j
@ControllerConfiguration
@Workflow(dependents = {
        @Dependent(type= AgentDeploymentPodFunctionDependentResource.class, activationCondition = AgentDeploymentPodFunctionDependentResource.MatchingDriverCondition.class)
})
public class AgentDeploymentReconciler implements Reconciler<AgentDeployment>, Cleaner<AgentDeployment> {

    @Override
    public DeleteControl cleanup(AgentDeployment resource, Context<AgentDeployment> context) throws Exception {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<AgentDeployment> reconcile(AgentDeployment resource, Context<AgentDeployment> context) throws Exception {
        return null;
    }
}
