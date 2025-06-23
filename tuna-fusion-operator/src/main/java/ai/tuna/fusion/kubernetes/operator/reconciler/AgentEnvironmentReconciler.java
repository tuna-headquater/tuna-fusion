package ai.tuna.fusion.kubernetes.operator.reconciler;

import ai.tuna.fusion.kubernetes.operator.crd.AgentEnvironment;
import ai.tuna.fusion.kubernetes.operator.crd.AgentEnvironmentStatus;
import ai.tuna.fusion.kubernetes.operator.dr.FissionEnvDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author robinqu
 */
@Workflow(
        dependents = {
                @Dependent(
                        type = FissionEnvDependentResource.class
                )
        }
)
@ControllerConfiguration(name="agentenvironment")
@Component
@Slf4j
public class AgentEnvironmentReconciler implements Reconciler<AgentEnvironment> {

    public static final String SELECTOR = "managed";

    @Override
    public UpdateControl<AgentEnvironment> reconcile(AgentEnvironment resource, Context<AgentEnvironment> context) throws Exception {
        var fissionEnvResource = context.getClient().genericKubernetesResources(
                FissionEnvDependentResource.API_VERSION,
                FissionEnvDependentResource.KIND
        )
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .get();

        if (Objects.isNull(fissionEnvResource) || StringUtils.isBlank(fissionEnvResource.getMetadata().getName())) {
            log.error("Fission env is not created for AgentEnvironment({})", resource.getMetadata());
            return UpdateControl.noUpdate();
        }
        AgentEnvironment updatePatch = new AgentEnvironment();
        updatePatch.getMetadata().setNamespace(resource.getMetadata().getNamespace());
        updatePatch.getMetadata().setName(resource.getMetadata().getName());
        var status = new AgentEnvironmentStatus();
        var fissionEnvStatus = new AgentEnvironmentStatus.FissionEnvStatus();
        fissionEnvStatus.setName(fissionEnvStatus.getName());
        updatePatch.setStatus(status);
        return UpdateControl.patchStatus(updatePatch);
    }
}
