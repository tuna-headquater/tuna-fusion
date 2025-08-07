package ai.tuna.fusion.kubernetes.operator.podpool.reconciler;

import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
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
@ControllerConfiguration(name = "podPoolReconciler")
@Workflow(dependents = {
        @Dependent(type = PodPoolDeploymentDependentResource.class),
        @Dependent(type = PodPoolServiceDependentResource.class)
})
public class PodPoolReconciler implements Reconciler<PodPool>, Cleaner<PodPool> {

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
        var deployment = deploy.get();
        var podPoolStatus = new PodPoolStatus();
        podPoolStatus.setDeploymentName(PodPoolResourceUtils.computePodPoolDeploymentName(resource));
        podPoolStatus.setGenericPodSelectors(PodPoolResourceUtils.computeGenericPodSelectors(resource));
        podPoolStatus.setAvailablePods(Optional.ofNullable(deployment.getStatus().getAvailableReplicas()).orElse(0));
        podPoolStatus.setHeadlessServiceName(PodPoolResourceUtils.computePodPoolServiceName(resource));
        var podPoolUpdate = new PodPool();
        podPoolUpdate.setStatus(podPoolStatus);
        podPoolUpdate.getMetadata().setName(resource.getMetadata().getName());
        podPoolUpdate.getMetadata().setNamespace(resource.getMetadata().getNamespace());
        return UpdateControl.patchStatus(podPoolUpdate);
    }

}
