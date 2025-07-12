package ai.tuna.fusion.kubernetes.operator.podpool.reconciler;

import ai.tuna.fusion.kubernetes.operator.podpool.PodPoolResourceUtils;
import ai.tuna.fusion.kubernetes.operator.podpool.dr.PodPoolDeploymentDependentResource;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.crd.podpool.PodPoolStatus;
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
        @Dependent(type = PodPoolDeploymentDependentResource.class)
})
public class PodPoolReconciler implements Reconciler<PodPool> {

    public static final String SELECTOR = "managed-by-pod-pool-reconciler";

    @Override
    public UpdateControl<PodPool> reconcile(PodPool resource, Context<PodPool> context) throws Exception {
        var deploy = PodPoolResourceUtils.getPodPoolDeployment(resource, context.getClient());
        if (deploy.isEmpty()) {
            log.warn("Deploy is not ready yet for PodPool {}", resource.getMetadata().getName());
            return UpdateControl.noUpdate();
        }
        var deployment = deploy.get();

        var orphanPods = PodPoolResourceUtils.listOrphanPods(resource, context.getClient());
        var podPoolStatus = new PodPoolStatus();
        podPoolStatus.setDeploymentName(PodPoolResourceUtils.getPodPoolDeploymentName(resource));
        podPoolStatus.setGenericPodSelectors(PodPoolResourceUtils.computePodSelectors(resource));
        podPoolStatus.setAvailablePods(deployment.getStatus().getAvailableReplicas());
        podPoolStatus.setOrphanPods(orphanPods.size());
        var podPoolUpdate = new PodPool();
        podPoolUpdate.setStatus(podPoolStatus);
        podPoolUpdate.getMetadata().setName(resource.getMetadata().getName());
        podPoolUpdate.getMetadata().setNamespace(resource.getMetadata().getNamespace());
        return UpdateControl.patchStatus(podPoolUpdate);
    }
}
