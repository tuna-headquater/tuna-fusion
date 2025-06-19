package ai.tuna.fusion.kubernetes.operator.reconciler;

import ai.tuna.fusion.kubernetes.operator.crd.AgentBuild;
import ai.tuna.fusion.kubernetes.operator.crd.AgentBuildStatus;
import ai.tuna.fusion.kubernetes.operator.crd.AgentDeployment;
import ai.tuna.fusion.kubernetes.operator.crd.AgentDeploymentStatus;
import ai.tuna.fusion.kubernetes.operator.dr.AgentBuildJobDependentResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * @author robinqu
 */
@Workflow(
    dependents = {
        @Dependent(
                type = AgentBuildJobDependentResource.class,
                activationCondition = AgentBuildJobDependentResource.IsJobRequiredCondition.class
        )
    }
)
@Component
@ControllerConfiguration(name="agentbuild")
@Slf4j
public class AgentBuildReconciler implements Reconciler<AgentBuild>, Cleaner<AgentBuild> {

    public static final String SELECTOR = "managed";

    private final KubernetesClient client;

    public AgentBuildReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public DeleteControl cleanup(AgentBuild resource, Context<AgentBuild> context) throws Exception {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<AgentBuild> reconcile(AgentBuild resource, Context<AgentBuild> context) throws Exception {
        // 获取关联的 AgentDeploymentCR
        var agentDeployment = client.resources(AgentDeployment.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(getReferencedAgentDeploymentName(resource))
                .get();

        if(Objects.isNull(agentDeployment)) {
            throw new IllegalStateException("AgentDeployment referenced doesn't exist");
        }

        var jobResource = context.getSecondaryResource(Job.class)
                .orElse(null);

        if (!Objects.isNull(jobResource)) {
            var fullJob = getBuildJob(
                    jobResource.getMetadata().getName(),
                    jobResource.getMetadata().getNamespace()
            );
            var jobStatus = fullJob.getStatus();
            log.info("Job(meta={}) has already been created: ready={}, failed={}, active={}, succeeded={}", fullJob.getMetadata(), jobStatus.getReady(), jobStatus.getFailed(), jobStatus.getActive(), jobStatus.getSucceeded());
            var agentBuildPatch = new AgentBuild();
            agentBuildPatch.getMetadata().setName(resource.getMetadata().getName());
            agentBuildPatch.getMetadata().setNamespace(resource.getMetadata().getNamespace());

            var status = new AgentBuildStatus();
            agentBuildPatch.setStatus(status);
            if(Optional.ofNullable(jobStatus.getSucceeded()).orElse(0) >= jobResource.getSpec().getCompletions()) {
                status.setPhase(AgentBuildStatus.Phase.Succeeded);
            } else if (Optional.ofNullable(jobStatus.getFailed()).orElse(0) >= jobResource.getSpec().getBackoffLimit()) {
                status.setPhase(AgentBuildStatus.Phase.Failed);
            } else if (Optional.ofNullable(jobStatus.getActive()).orElse(0)>0) {
                status.setPhase(AgentBuildStatus.Phase.Pending);
            } else  {
                status.setPhase(AgentBuildStatus.Phase.Scheduled);
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


    AgentDeploymentStatus updatedDeploymentStatus(AgentBuild agentBuild) {
        var agentDeploymentStatus = new AgentDeploymentStatus();
        var buildInfo = new AgentDeploymentStatus.BuildInfo();
        buildInfo.setName(agentBuild.getMetadata().getName());
        buildInfo.setStartTimestamp(Instant.now().getEpochSecond());
        agentDeploymentStatus.setCurrentBuild(buildInfo);
        return agentDeploymentStatus;
    }

    private Job getBuildJob(String jobName, String ns) {
        return client.resources(Job.class)
                .inNamespace(ns)
                .withName(jobName)
                .get();
    }


    private String getReferencedAgentDeploymentName(AgentBuild agentBuild) {
        return agentBuild.getMetadata().getOwnerReferences().stream()
                .filter(ownerReference -> StringUtils.equals(ownerReference.getKind(),HasMetadata.getKind(AgentDeployment.class)))
                .findFirst()
                .map(OwnerReference::getName)
                .orElseThrow();
    }
}
