package ai.tuna.fusion.kubernetes.operator.reconciler;

import ai.tuna.fusion.kubernetes.operator.ResourceUtils;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import io.javaoperatorsdk.operator.api.reconciler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author robinqu
 */
@Component
@ControllerConfiguration(name="agentdeployment")
@Slf4j
public class AgentDeploymentReconciler implements Reconciler<AgentDeployment>, Cleaner<AgentDeployment> {

    public static String FISSION_RESOURCES_FINALIZER = "ai.tuna.fusion/cleanup-fission-resources";

    @Override
    public DeleteControl cleanup(AgentDeployment resource, Context<AgentDeployment> context) throws Exception {
        deleteFissionResources(resource, context);
        resource.getFinalizers().remove(FISSION_RESOURCES_FINALIZER);
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<AgentDeployment> reconcile(AgentDeployment resource, Context<AgentDeployment> context) throws Exception {
        // handling deletion
        if (resource.getMetadata().getDeletionTimestamp() == null
                && !resource.getMetadata().getFinalizers().contains(FISSION_RESOURCES_FINALIZER)) {
            log.info("Add fission resource finalizer to agent deployment: name={},ns={}", resource.getMetadata().getName(), resource.getMetadata().getNamespace());
            var patch = new AgentDeployment();
            for (var finalizer : resource.getFinalizers()) {
                patch.addFinalizer(finalizer);
            }
            patch.addFinalizer(FISSION_RESOURCES_FINALIZER);
            patch.getMetadata().setNamespace(resource.getMetadata().getNamespace());
            patch.getMetadata().setName(resource.getMetadata().getName());

            return UpdateControl.patchResource(patch);
        }
        return UpdateControl.noUpdate();
    }

    private void deleteFissionResources(AgentDeployment resource, Context<AgentDeployment> context) {
        ResourceUtils.deleteFissionFunction(resource, context.getClient());
        ResourceUtils.deleteFissionRoute(resource, context.getClient());
    }

}
