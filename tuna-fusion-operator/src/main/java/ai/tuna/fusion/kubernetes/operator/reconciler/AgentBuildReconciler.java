package ai.tuna.fusion.kubernetes.operator.reconciler;

import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriverRegistry;
import ai.tuna.fusion.metadata.crd.*;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static ai.tuna.fusion.kubernetes.operator.ResourceUtils.*;

/**
 * @author robinqu
 */
//@Workflow(
//    dependents = {
//        @Dependent(
//                type = AgentBuildJobDependentResource.class,
//                reconcilePrecondition = AgentBuildJobDependentResource.IsJobRequiredCondition.class
//        )
//    }
//)
@Component
@ControllerConfiguration(name="agentbuild")
@Slf4j
public class AgentBuildReconciler implements Reconciler<AgentBuild>, Cleaner<AgentBuild> {

    private final ProvisioningDriverRegistry driverRegistry;

    public AgentBuildReconciler(ProvisioningDriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    @Override
    public List<EventSource<?, AgentBuild>> prepareEventSources(EventSourceContext<AgentBuild> context) {
        List<EventSource<?, AgentBuild>> eventSources = new ArrayList<>();
        driverRegistry.allDrivers().forEach(driver -> {
            eventSources.addAll(EventSourceUtils.dependentEventSources(
                    context, driver.agentBuild().dependentResource().toArray(new DependentResource[0])));
        });
        return eventSources;
    }

    @Override
    public DeleteControl cleanup(AgentBuild resource, Context<AgentBuild> context)  {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<AgentBuild> reconcile(AgentBuild resource, Context<AgentBuild> context) {
        var agentDeployment = getReferencedAgentDeployment(context.getClient(), resource).orElseThrow();
        var agentEnvironment = getReferencedAgentEnvironment(context.getClient(), agentDeployment).orElseThrow();
        var driver = driverRegistry.getDriver(agentEnvironment.getSpec().getDriverType());
        driver.agentBuild().workflow().reconcile(resource, context);
        return driver.agentBuild().statusUpdate(resource, context);
    }




}
