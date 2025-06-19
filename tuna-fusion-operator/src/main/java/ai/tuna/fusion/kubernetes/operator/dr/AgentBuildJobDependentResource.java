package ai.tuna.fusion.kubernetes.operator.dr;

import ai.tuna.fusion.kubernetes.operator.crd.AgentBuild;
import ai.tuna.fusion.kubernetes.operator.crd.AgentBuildStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

import static ai.tuna.fusion.kubernetes.operator.reconciler.AgentBuildReconciler.SELECTOR;

/**
 * @author robinqu
 */
@KubernetesDependent(
        informer = @Informer(labelSelector = SELECTOR)
)
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

    @Override
    public Result<Job> match(Job actualResource, Job desired, AgentBuild primary, Context<AgentBuild> context) {
        boolean match = StringUtils.equals(actualResource.getMetadata().getName(), desired.getMetadata().getName());
        return Result.nonComputed(match);
    }

    @Override
    protected Job desired(AgentBuild primary, Context<AgentBuild> context) {
        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels(SELECTOR, "true")
                .endMetadata()
                .withNewSpec()
//                .withTtlSecondsAfterFinished(60)
                .withBackoffLimit(1)
                .withCompletions(1)
                .withActiveDeadlineSeconds(60 * 10L)
                .withNewTemplate()
                .withNewMetadata()
                .withNamespace(primary.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewInitContainer()
                .withName("builder-init-container")
                .withImage("busybox:latest")
                .withCommand("sh", "-c", "echo -e '%s' > /workspace/build.sh && chmod +x /workspace/build.sh".formatted(primary.getSpec().getBuildScript()))
                .addNewVolumeMount()
                .withName("builder-script-volume")
                .withMountPath("/workspace")
                .endVolumeMount()
                .endInitContainer()
                .addNewContainer()
                .withName("build-container")
                .withImage(primary.getSpec().getBuilderImage())
                .withCommand("sh", "/workspace/build.sh")
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
