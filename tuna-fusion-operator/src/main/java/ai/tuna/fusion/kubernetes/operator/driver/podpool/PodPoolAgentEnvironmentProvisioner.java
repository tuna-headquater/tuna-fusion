package ai.tuna.fusion.kubernetes.operator.driver.podpool;

import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriver;
import ai.tuna.fusion.kubernetes.operator.driver.podpool.dr.PodPoolArchivePVCDependentResource;
import ai.tuna.fusion.kubernetes.operator.driver.podpool.dr.PodPoolDeploymentDependentResource;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.AgentEnvironmentStatus;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowBuilder;

import java.util.Arrays;
import java.util.List;


/**
 * @author robinqu
 */
public class PodPoolAgentEnvironmentProvisioner implements ProvisioningDriver.Provisioner<AgentEnvironment> {
    public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "managed-by-pod-pool-agent-environment-provisioner";


    private final PodPoolArchivePVCDependentResource archivePvcDependentResource;
    private final PodPoolDeploymentDependentResource deploymentDependentResource;
    Workflow<AgentEnvironment> workflow;

    public PodPoolAgentEnvironmentProvisioner() {
        archivePvcDependentResource = new PodPoolArchivePVCDependentResource();
        deploymentDependentResource = new PodPoolDeploymentDependentResource();
        workflow = new WorkflowBuilder<AgentEnvironment>()
                .addDependentResource(archivePvcDependentResource)
                .addDependentResource(deploymentDependentResource)
                .build();
        Arrays.asList(archivePvcDependentResource, deploymentDependentResource)
                .forEach(
                        dr ->
                                dr.configureWith(
                                        new KubernetesDependentResourceConfigBuilder()
                                                .withKubernetesDependentInformerConfig(
                                                        InformerConfiguration.builder(dr.resourceType())
                                                                .withLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR)
                                                                .build())
                                                .build()));
    }

    @Override
    public Workflow<AgentEnvironment> workflow() {
        return workflow;
    }

    @Override
    public List<DependentResource<?, AgentEnvironment>> dependentResource() {
        return List.of(
                archivePvcDependentResource,
                deploymentDependentResource
        );
    }

    @Override
    public UpdateControl<AgentEnvironment> statusUpdate(AgentEnvironment agentEnvironment, Context<AgentEnvironment> context) {
        var deploy = PodPoolResourceUtils.getPodPoolDeployment(agentEnvironment, context.getClient());
        if (deploy.isEmpty()) {
            return UpdateControl.noUpdate();
        }
        var deployment = deploy.get();

        var orphanPods = PodPoolResourceUtils.listOrphanPods(agentEnvironment, context.getClient());
        var status = new AgentEnvironmentStatus();
        var podPoolStatus = new AgentEnvironmentStatus.PodPoolStatus();
        podPoolStatus.setDeploymentName(PodPoolResourceUtils.getPodPoolDeploymentName(agentEnvironment));
        podPoolStatus.setGenericPodSelectors(PodPoolResourceUtils.computePodSelectors(agentEnvironment));
        podPoolStatus.setAvailablePods(deployment.getStatus().getAvailableReplicas());
        podPoolStatus.setOrphanPods(orphanPods.size());
        status.setPodPool(podPoolStatus);
        var agentEnv = new AgentEnvironment();
        agentEnv.setStatus(status);
        agentEnv.getMetadata().setName(agentEnvironment.getMetadata().getName());
        agentEnv.getMetadata().setNamespace(agentEnvironment.getMetadata().getNamespace());
        return UpdateControl.patchStatus(agentEnv);
    }


}
