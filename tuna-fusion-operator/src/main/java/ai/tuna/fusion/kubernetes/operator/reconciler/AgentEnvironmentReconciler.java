package ai.tuna.fusion.kubernetes.operator.reconciler;

import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriverRegistry;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
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

@ControllerConfiguration(
        name="agentenvironment"
)
@Component
@Slf4j
public class AgentEnvironmentReconciler implements Reconciler<AgentEnvironment> {

    private final ProvisioningDriverRegistry driverRegistry;

    public AgentEnvironmentReconciler(ProvisioningDriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    @Override
    public List<EventSource<?, AgentEnvironment>> prepareEventSources(EventSourceContext<AgentEnvironment> context) {
        List<EventSource<?, AgentEnvironment>> eventSources = new ArrayList<>();
        driverRegistry.allDrivers().forEach(driver -> {
            eventSources.addAll(EventSourceUtils.dependentEventSources(
                    context, driver.agentEnvironment().dependentResource().toArray(new DependentResource[0])));
        });
        return eventSources;
    }

    @Override
    public UpdateControl<AgentEnvironment> reconcile(AgentEnvironment resource, Context<AgentEnvironment> context) throws Exception {
        var driver = driverRegistry.getDriver(resource.getSpec().getDriverType());
        driver.agentEnvironment().workflow().reconcile(resource, context);
        return driver.agentEnvironment().statusUpdate(resource, context);
    }
}
