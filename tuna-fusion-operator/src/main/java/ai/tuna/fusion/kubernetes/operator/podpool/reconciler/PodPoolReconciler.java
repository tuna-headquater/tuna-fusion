package ai.tuna.fusion.kubernetes.operator.podpool.reconciler;

import ai.tuna.fusion.kubernetes.operator.podpool.PodPoolResourceUtils;
import ai.tuna.fusion.kubernetes.operator.podpool.dr.PodPoolDeploymentDependentResource;
import ai.tuna.fusion.kubernetes.operator.podpool.dr.PodPoolServiceDependentResource;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.crd.podpool.PodPoolStatus;
import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author robinqu
 */
@Component
@Slf4j
@ControllerConfiguration
@Workflow(dependents = {
        @Dependent(type = PodPoolDeploymentDependentResource.class),
        @Dependent(type = PodPoolServiceDependentResource.class)
})
public class PodPoolReconciler implements Reconciler<PodPool>, Cleaner<PodPool> {

    public static final String SELECTOR = "fusion.tuna.ai/managed-by-pp";

    @Override
    public DeleteControl cleanup(PodPool resource, Context<PodPool> context)  {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<PodPool> reconcile(PodPool resource, Context<PodPool> context) {
        var deploy = PodPoolResourceUtils.getPodPoolDeployment(resource, context.getClient());
        if (deploy.isEmpty()) {
            log.warn("Deploy is not ready yet for PodPool {}", resource.getMetadata().getName());
            return UpdateControl.noUpdate();
        }
        cleanupOrphanPods(resource, context);
        var deployment = deploy.get();
        var podPoolStatus = new PodPoolStatus();
        podPoolStatus.setDeploymentName(PodPoolResourceUtils.getPodPoolDeploymentName(resource));
        podPoolStatus.setGenericPodSelectors(PodPoolResourceUtils.computeGenericPodSelectors(resource));
        podPoolStatus.setAvailablePods(Optional.ofNullable(deployment.getStatus().getAvailableReplicas()).orElse(0));
        podPoolStatus.setHeadlessServiceName(PodPoolResourceUtils.computePodPoolServiceName(resource));
        var podPoolUpdate = new PodPool();
        podPoolUpdate.setStatus(podPoolStatus);
        podPoolUpdate.getMetadata().setName(resource.getMetadata().getName());
        podPoolUpdate.getMetadata().setNamespace(resource.getMetadata().getNamespace());
        return UpdateControl.patchStatus(podPoolUpdate)
                .rescheduleAfter(10, TimeUnit.SECONDS);
    }

    private static final long TTL_IN_SECONDS_FOR_SPECIALIZED_POD = 60 * 60 * 24;
    private void cleanupOrphanPods(PodPool resource, Context<PodPool> context) {
        var specializedPods = PodPoolResourceUtils.listSpecializedPods(resource, context.getClient());
        for (var pod : specializedPods) {
            try {
                var creationTime = Instant.parse(pod.getMetadata().getCreationTimestamp());
                var isOrphan = creationTime.isBefore(Instant.now().minusSeconds(TTL_IN_SECONDS_FOR_SPECIALIZED_POD));
                if (isOrphan) {
                    log.debug("Found orphan pod for PodPool {}: {}", resource.getMetadata().getName(), pod.getMetadata().getName());
                    Preconditions.checkState(ResourceUtils.deleteResource(context.getClient(), pod.getMetadata().getNamespace(), pod.getMetadata().getName(), Pod.class), "Should have pod %s deleted", pod.getMetadata().getName());
                }
            } catch (Exception e) {
                log.error("Exception occurred during checking specialized pod {}: {}", pod, e.getMessage(), e);
            }
        }
    }
}
