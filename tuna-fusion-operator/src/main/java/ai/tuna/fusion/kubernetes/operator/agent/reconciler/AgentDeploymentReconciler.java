package ai.tuna.fusion.kubernetes.operator.agent.reconciler;

import ai.tuna.fusion.metadata.crd.AgentResourceUtils;
import ai.tuna.fusion.kubernetes.operator.agent.dr.AgentDeploymentPodFunctionDependentResource;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentStatus;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
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

    public static final String SELECTOR = "fusion.tuna.ai/managed-by-ad";

    @Override
    public DeleteControl cleanup(AgentDeployment resource, Context<AgentDeployment> context) {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<AgentDeployment> reconcile(AgentDeployment resource, Context<AgentDeployment> context) {
        var agentEnvironment = AgentResourceUtils.getReferencedAgentEnvironment(context.getClient(), resource).orElseThrow(()-> new IllegalStateException("Agent Environment not found"));
        AgentDeployment patch = new AgentDeployment();
        patch.getMetadata().setNamespace(resource.getMetadata().getNamespace());
        patch.getMetadata().setName(resource.getMetadata().getName());
        ResourceUtils.addOwnerReference(patch, agentEnvironment);
        var status = new AgentDeploymentStatus();
        var driverType = agentEnvironment.getSpec().getDriver().getType();
        status.setDriverType(driverType);
        if (driverType == AgentEnvironmentSpec.DriverType.PodPool) {
            context.getSecondaryResource(PodFunction.class).
                    ifPresent(podFunction -> {
                        var podFunctionInfo = new AgentDeploymentStatus.PodFunctionInfo();
                        podFunctionInfo.setFunctionName(podFunction.getMetadata().getName());
                        status.setFunction(podFunctionInfo);
                    });
        }
        return UpdateControl.patchResourceAndStatus(patch);
    }
}
