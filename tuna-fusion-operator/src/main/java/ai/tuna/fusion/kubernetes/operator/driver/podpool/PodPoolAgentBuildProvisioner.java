package ai.tuna.fusion.kubernetes.operator.driver.podpool;

import ai.tuna.fusion.kubernetes.operator.ResourceUtils;
import ai.tuna.fusion.kubernetes.operator.driver.podpool.dr.PodPoolBuildJobDependentResource;
import ai.tuna.fusion.kubernetes.operator.driver.ProvisioningDriver;
import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentBuildStatus;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import ai.tuna.fusion.metadata.crd.AgentDeploymentStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static ai.tuna.fusion.kubernetes.operator.ResourceUtils.getReferencedAgentDeployment;
import static ai.tuna.fusion.kubernetes.operator.ResourceUtils.getReferencedAgentDeploymentName;

/**
 * @author robinqu
 */
@Slf4j
public class PodPoolAgentBuildProvisioner implements ProvisioningDriver.Provisioner<AgentBuild> {

    public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "managed-by-pod-pool-agent-build-provisioner";


    private final PodPoolBuildJobDependentResource podPoolBuildJobDependentResource;
    private final Workflow<AgentBuild> workflow;


    public PodPoolAgentBuildProvisioner() {
        podPoolBuildJobDependentResource = new PodPoolBuildJobDependentResource();
        workflow = new WorkflowBuilder<AgentBuild>()
                .addDependentResourceAndConfigure(podPoolBuildJobDependentResource)
                .withReconcilePrecondition(new PodPoolBuildJobDependentResource.IsJobRequiredCondition())
                .build();

        Arrays.asList(podPoolBuildJobDependentResource)
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
    public Workflow<AgentBuild> workflow() {
        return workflow;
    }

    @Override
    public UpdateControl<AgentBuild> statusUpdate(AgentBuild resource, Context<AgentBuild> context) {
        var client = context.getClient();
        // 获取关联的 AgentDeploymentCR
        var agentDeployment = getReferencedAgentDeployment(client, resource).orElseThrow();

        var jobResource = context.getSecondaryResource(Job.class)
                .orElse(null);

        if (!Objects.isNull(jobResource)) {
            var fullJob = getBuildJob(
                    context.getClient(),
                    jobResource.getMetadata().getName(),
                    jobResource.getMetadata().getNamespace()
            );
            var jobStatus = fullJob.getStatus();
            log.info("Job(namespace={},name={}) has already been created: ready={}, failed={}, active={}, succeeded={}", fullJob.getMetadata().getNamespace(), fullJob.getMetadata().getName(), jobStatus.getReady(), jobStatus.getFailed(), jobStatus.getActive(), jobStatus.getSucceeded());
            var agentBuildPatch = new AgentBuild();
            agentBuildPatch.getMetadata().setName(resource.getMetadata().getName());
            agentBuildPatch.getMetadata().setNamespace(resource.getMetadata().getNamespace());

            var status = new AgentBuildStatus();
            agentBuildPatch.setStatus(status);
            if(Optional.ofNullable(jobStatus.getSucceeded()).orElse(0) >= jobResource.getSpec().getCompletions()) {
                status.setPhase(AgentBuildStatus.Phase.Succeeded);
            } else if (Optional.ofNullable(jobStatus.getFailed()).orElse(0) > jobResource.getSpec().getBackoffLimit()) {
                status.setPhase(AgentBuildStatus.Phase.Failed);
            } else if (Optional.ofNullable(jobStatus.getActive()).orElse(0)>0) {
                status.setPhase(AgentBuildStatus.Phase.Running);
            } else  {
                status.setPhase(AgentBuildStatus.Phase.Scheduled);
            }
            AgentBuildStatus.JobPodInfo jobPodInfo = new AgentBuildStatus.JobPodInfo();
            status.setJobPod(jobPodInfo);
            var jobPod = getJobPod(context.getClient(), jobResource.getMetadata().getName(), jobResource.getMetadata().getNamespace());
            jobPodInfo.setPodName(jobPod.map(pod -> pod.getMetadata().getName()).orElseThrow());
            jobPodInfo.setPodPhase(jobPod.map(pod -> pod.getStatus().getPhase()).orElseThrow());
            log.info("JobPodInfo: {}", jobPodInfo);
            if (ResourceUtils.isJobTerminalPhase(jobPodInfo.getPodPhase())) {
                var logs = client.pods().inNamespace(jobResource.getMetadata().getNamespace())
                        .withName(jobPodInfo.getPodName())
                        .getLog(true);
                jobPodInfo.setLogs(logs);
            }


            // 更新 AgentDeployment 状态
            if (agentBuildPatch.getStatus().getPhase() == AgentBuildStatus.Phase.Succeeded || agentBuildPatch.getStatus().getPhase() == AgentBuildStatus.Phase.Failed) {
                log.info("Patching terminal AgentDeployment: {}", agentDeployment.getMetadata().getName());
                AgentDeployment patchDeployment = new AgentDeployment();
                patchDeployment.setStatus(updatedDeploymentStatus(resource));
                client.resources(AgentDeployment.class)
                        .inNamespace(resource.getMetadata().getNamespace())
                        .withName(getReferencedAgentDeploymentName(resource))
                        .patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY), patchDeployment);
            }
            // 更新 AgentBuild 状态
            return UpdateControl.patchStatus(agentBuildPatch);
        }
        log.debug("Job is not created or already finished for AgentBuild: {}", resource.getMetadata().getName());
        return UpdateControl.noUpdate();
    }

    @Override
    public List<DependentResource<?, AgentBuild>> dependentResource() {
        return List.of(podPoolBuildJobDependentResource);
    }

    private Optional<Pod> getJobPod(KubernetesClient client, String jobName, String namespace) {
        return client.pods()
                .inNamespace(namespace)
                .withLabel("job-name", jobName)
                .list()
                .getItems()
                .stream()
                .findFirst();
    }


    AgentDeploymentStatus updatedDeploymentStatus(AgentBuild agentBuild) {
        var agentDeploymentStatus = new AgentDeploymentStatus();
        var buildInfo = new AgentDeploymentStatus.BuildInfo();
        buildInfo.setName(agentBuild.getMetadata().getName());
        buildInfo.setStartTimestamp(Instant.now().getEpochSecond());
        agentDeploymentStatus.setCurrentBuild(buildInfo);
        return agentDeploymentStatus;
    }

    private Job getBuildJob(KubernetesClient client,String jobName, String ns) {
        return client.batch()
                .v1()
                .jobs()
                .inNamespace(ns)
                .withName(jobName)
                .get();
    }
}
