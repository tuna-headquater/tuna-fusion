package ai.tuna.fusion.kubernetes.operator.agent.reconciler;

import ai.tuna.fusion.kubernetes.operator.agent.dr.AgentEnvironmentPodPoolDependentResource;
import ai.tuna.fusion.kubernetes.operator.config.OperatorProperties;
import ai.tuna.fusion.metadata.crd.AgentResourceUtils;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

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
@ControllerConfiguration(name = "agentEnvironmentReconciler")
public class AgentEnvironmentReconciler implements Reconciler<AgentEnvironment>, Cleaner<AgentEnvironment> {

    public static final String SELECTOR = "fusion.tuna.ai/managed-by-ae";

    private final OperatorProperties operatorProperties;

    public AgentEnvironmentReconciler(OperatorProperties operatorProperties) {
        this.operatorProperties = operatorProperties;
    }

    @Override
    public DeleteControl cleanup(AgentEnvironment resource, Context<AgentEnvironment> context) {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<AgentEnvironment> reconcile(AgentEnvironment resource, Context<AgentEnvironment> context) {
        if (resource.getSpec().getDriver().getType() != AgentEnvironmentSpec.DriverType.PodPool) {
            throw new IllegalArgumentException("Only PodPool is supported now");
        }
        var podPool = AgentResourceUtils.getPodPoolForAgentEnvironment(resource, context.getClient());
        AgentEnvironment update = new AgentEnvironment();
        update.getMetadata().setName(resource.getMetadata().getName());
        update.getMetadata().setNamespace(resource.getMetadata().getNamespace());
        var status = new AgentEnvironmentStatus();
        update.setStatus(status);
        podPool.map(PodPool::getStatus).ifPresent(podPoolStatus -> {
            var podPoolInfo = new AgentEnvironmentStatus.PodPoolInfo();
            podPoolInfo.setName(podPool.get().getMetadata().getName());
            podPoolInfo.setStatus(podPoolStatus);
            status.setPodPool(podPoolInfo);
        });
        if (Objects.isNull(resource.getSpec().getExecutor())) {
            log.debug("[reconcile] spec.executor is absent for AgentEnvironment {}, will update with app properties", ResourceUtils.computeResourceMetaKey(resource));
            update.getSpec().setExecutor(operatorProperties.getExecutor());
        }
        log.info("[reconcile] Patching status for Agent Environment {}: {}", resource.getMetadata().getName(), status);
        return UpdateControl.patchResourceAndStatus(update);

    }


}
