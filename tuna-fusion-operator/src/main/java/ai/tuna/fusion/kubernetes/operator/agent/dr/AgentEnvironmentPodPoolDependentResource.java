package ai.tuna.fusion.kubernetes.operator.agent.dr;

import ai.tuna.fusion.kubernetes.operator.agent.reconciler.AgentEnvironmentReconciler;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * @author robinqu
 */
@KubernetesDependent(informer = @Informer(labelSelector = AgentEnvironmentReconciler.SELECTOR))
public class AgentEnvironmentPodPoolDependentResource extends CRUDKubernetesDependentResource<PodPool, AgentEnvironment> {

    public static class MatchingDriverCondition implements Condition<PodPool, AgentEnvironment> {
        @Override
        public boolean isMet(DependentResource<PodPool, AgentEnvironment> dependentResource, AgentEnvironment primary, Context<AgentEnvironment> context) {
            return primary.getSpec().getDriver().getType() == AgentEnvironmentSpec.DriverType.PodPool;
        }
    }

    @Override
    protected PodPool desired(AgentEnvironment primary, Context<AgentEnvironment> context) {
        var podPool = new PodPool();
        podPool.getMetadata().setName(primary.getMetadata().getName());
        podPool.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        podPool.setSpec(primary.getSpec().getDriver().getPodPoolSpec());
        return podPool;
    }
}
