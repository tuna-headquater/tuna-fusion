package ai.tuna.fusion.kubernetes.operator.agent.reconciler;

import ai.tuna.fusion.kubernetes.operator.agent.dr.AgentEnvironmentPodPoolDependentResource;
import ai.tuna.fusion.kubernetes.operator.podpool.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author robinqu
 */
@Workflow(dependents = {
    @Dependent(
        type = AgentEnvironmentPodPoolDependentResource.class,
        activationCondition = AgentEnvironmentPodPoolDependentResource.MatchingDriverCondition.class
    )
})
@Component
@Slf4j
@ControllerConfiguration
public class AgentEnvironmentReconciler implements Reconciler<AgentEnvironment>, Cleaner<AgentEnvironment> {

    public static final String SELECTOR = "fusion.tuna.ai/managed-by-ae";

    @Override
    public DeleteControl cleanup(AgentEnvironment resource, Context<AgentEnvironment> context) {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<AgentEnvironment> reconcile(AgentEnvironment resource, Context<AgentEnvironment> context) {
        if (resource.getSpec().getDriver().getType() != AgentEnvironmentSpec.DriverType.PodPool) {
            throw new IllegalArgumentException("Only PodPool is supported now");
        }
        var podPool = PodPoolResourceUtils.getPodPoolForAgentEnvironment(resource, context.getClient());
        var podPoolStatus = podPool.map(PodPool::getStatus);
        if(podPoolStatus.isPresent()) {
            AgentEnvironment update = new AgentEnvironment();
            update.getMetadata().setName(resource.getMetadata().getName());
            update.getMetadata().setNamespace(resource.getMetadata().getNamespace());
            var status = new AgentEnvironmentStatus();
            var podPoolInfo = new AgentEnvironmentStatus.PodPoolInfo();
            podPoolInfo.setName(podPool.get().getMetadata().getName());
            podPoolInfo.setStatus(podPoolStatus.get());
            status.setPodPool(podPoolInfo);
            return UpdateControl.patchStatus(update);
        }
        log.warn("PodPool for Agent Environment {} is not ready yet", resource.getMetadata().getName());
        return UpdateControl.noUpdate();

    }


}
