package ai.tuna.fusion.kubernetes.operator.podpool.reconciler;

import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author robinqu
 */
@Slf4j
@Component
@ControllerConfiguration(name = "podFunctionReconciler")
public class PodFunctionReconciler implements Reconciler<PodFunction> {
    @Override
    public UpdateControl<PodFunction> reconcile(PodFunction resource, Context<PodFunction> context) throws Exception {
        var podPool = PodPoolResourceUtils.getReferencedPodPool(resource, context.getClient()).orElseThrow();
        PodFunction patch = new PodFunction();
        patch.getMetadata().setName(resource.getMetadata().getName());
        patch.getMetadata().setNamespace(resource.getMetadata().getNamespace());
        ResourceUtils.addOwnerReference(patch, podPool, false);
        patch.setStatus(new PodFunctionStatus());
        return UpdateControl.patchResourceAndStatus(patch);
    }
}
