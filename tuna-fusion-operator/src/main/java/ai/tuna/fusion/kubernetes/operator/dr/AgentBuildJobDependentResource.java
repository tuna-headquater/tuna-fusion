package ai.tuna.fusion.kubernetes.operator.dr;

import ai.tuna.fusion.kubernetes.operator.ResourceUtils;
import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentBuildStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.BooleanWithUndefined;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

import static ai.tuna.fusion.kubernetes.operator.reconciler.AgentBuildReconciler.SELECTOR;

/**
 * @author robinqu
 */
@KubernetesDependent(
        informer = @Informer(labelSelector = SELECTOR)
)
@Slf4j
public class AgentBuildJobDependentResource extends CRUDKubernetesDependentResource<Job, AgentBuild> {

    public static class IsJobRequiredCondition implements Condition<Job, AgentBuild> {
        @Override
        public boolean isMet(DependentResource<Job, AgentBuild> dependentResource, AgentBuild primary, Context<AgentBuild> context) {
            var phase = Optional.ofNullable(primary.getStatus())
                    .map(AgentBuildStatus::getPhase)
                    .orElse(AgentBuildStatus.Phase.Pending);
            return phase == AgentBuildStatus.Phase.Pending;
        }
    }

//
//    @Override
//    public Result<Job> match(Job actualResource, Job desired, AgentBuild primary, Context<AgentBuild> context) {
//        boolean match = StringUtils.equals(actualResource.getMetadata().getName(), desired.getMetadata().getName());
//        return Result.nonComputed(match);
//    }

    @Override
    protected Job desired(AgentBuild primary, Context<AgentBuild> context) {
//        log.debug("Creating Job DR for build {}", primary.getMetadata());
        var agentDeployment = ResourceUtils.getReferencedAgentDeployment(context.getClient(), primary);
        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels(SELECTOR, "true")
                .endMetadata()
                .withNewSpec()
                .withTtlSecondsAfterFinished(60*60)
                // disallow retry
                .withBackoffLimit(0)
                .withCompletions(1)
                .withActiveDeadlineSeconds(60 * 10L)
                .withNewTemplate()
                .withNewMetadata()
                .withNamespace(primary.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withServiceAccountName(primary.getSpec().getServiceAccountName())
                .withRestartPolicy("Never")
                .addNewInitContainer()
                .withName("builder-init-container")
                .withImage("busybox:latest")
                .withCommand("sh", "-c", "echo -e '%s' > /workspace/build.sh && chmod +x /workspace/build.sh && cat /workspace/build.sh".formatted(primary.getSpec().getBuildScript()))
                .addNewVolumeMount()
                .withName("builder-script-volume")
                .withMountPath("/workspace")
                .endVolumeMount()
                .endInitContainer()
                .addNewContainer()
                .withName("build-container")
                .withImage(primary.getSpec().getBuilderImage())
                .withCommand("sh", "/workspace/build.sh")
                .addToEnv(
                        new EnvVar("FUNCTION_NAME", agentDeployment.getMetadata().getName(), null),
                        new EnvVar("FUNCTION_ENV", agentDeployment.getSpec().getEnvironmentName(), null),
                        new EnvVar("NAMESPACE", primary.getMetadata().getNamespace(), null),
                        new EnvVar("CATALOGUE_NAME", ResourceUtils.getReferenceAgentCatalogueName(agentDeployment), null)
                )
                .addNewVolumeMount()
                .withMountPath("/workspace")
                .withName("builder-script-volume")
                .endVolumeMount()
                .endContainer()
                .addNewVolume()
                .withName("builder-script-volume")
                .withNewEmptyDir()
                .endEmptyDir()
                .and()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private String jobName(AgentBuild primary) {
        return String.format("build-%s", primary.getMetadata().getName());
    }
}
