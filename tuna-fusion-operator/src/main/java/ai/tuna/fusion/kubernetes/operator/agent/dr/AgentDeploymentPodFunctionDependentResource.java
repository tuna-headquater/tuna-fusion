package ai.tuna.fusion.kubernetes.operator.agent.dr;

import ai.tuna.fusion.metadata.crd.AgentResourceUtils;
import ai.tuna.fusion.kubernetes.operator.agent.reconciler.AgentDeploymentReconciler;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionSpec;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * @author robinqu
 */
@KubernetesDependent(informer = @Informer(labelSelector = AgentDeploymentReconciler.SELECTOR))
public class AgentDeploymentPodFunctionDependentResource extends CRUDKubernetesDependentResource<PodFunction, AgentDeployment> {

    public static class MatchingDriverCondition implements Condition<PodFunction, AgentDeployment> {
        @Override
        public boolean isMet(DependentResource<PodFunction, AgentDeployment> dependentResource, AgentDeployment primary, Context<AgentDeployment> context) {
            var agentEnvironment = AgentResourceUtils.getReferencedAgentEnvironment(context.getClient(), primary).orElseThrow();
            return agentEnvironment.getSpec().getDriver().getType() == AgentEnvironmentSpec.DriverType.PodPool;
        }
    }


    @Override
    protected PodFunction desired(AgentDeployment primary, Context<AgentDeployment> context) {
        var podFunction = new PodFunction();
        podFunction.getMetadata().setName(AgentResourceUtils.computeFunctionName(primary));
        podFunction.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        var podFunctionSpec = new PodFunctionSpec();
        podFunction.setSpec(podFunctionSpec);
        podFunctionSpec.setEntrypoint(primary.getSpec().getEntrypoint());
        return podFunction;
    }

}
