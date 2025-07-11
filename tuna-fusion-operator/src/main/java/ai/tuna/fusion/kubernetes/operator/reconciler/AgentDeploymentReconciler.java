package ai.tuna.fusion.kubernetes.operator.reconciler;

import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriverRegistry;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author robinqu
 */
@Component
@ControllerConfiguration(name="agentdeployment")
@Slf4j
public class AgentDeploymentReconciler implements Reconciler<AgentDeployment>, Cleaner<AgentDeployment> {


    private final ProvisioningDriverRegistry driverRegistry;

    public AgentDeploymentReconciler(ProvisioningDriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    @Override
    public DeleteControl cleanup(AgentDeployment resource, Context<AgentDeployment> context) throws Exception {
        return DeleteControl.defaultDelete();
    }

    @Override
    public List<EventSource<?, AgentDeployment>> prepareEventSources(EventSourceContext<AgentDeployment> context) {
        List<EventSource<?, AgentDeployment>> eventSources = new ArrayList<>();
        driverRegistry.allDrivers().forEach(driver -> {
            eventSources.addAll(EventSourceUtils.dependentEventSources(
                    context, driver.agentDeployment().dependentResource().toArray(new DependentResource[0])));
        });
        return eventSources;
    }

    @Override
    public UpdateControl<AgentDeployment> reconcile(AgentDeployment resource, Context<AgentDeployment> context) throws Exception {
        return UpdateControl.noUpdate();
    }


}
