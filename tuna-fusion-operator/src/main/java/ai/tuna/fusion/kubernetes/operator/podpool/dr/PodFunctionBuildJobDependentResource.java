package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.kubernetes.operator.podpool.PodPoolResourceUtils;
import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodFunctionBuildReconciler;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;


/**
 * @author robinqu
 */
@KubernetesDependent(informer = @Informer(labelSelector = PodFunctionBuildReconciler.SELECTOR))
@Slf4j
public class PodFunctionBuildJobDependentResource extends CRUDKubernetesDependentResource<Job, PodFunctionBuild> {

    public static class IsJobRequiredCondition implements Condition<Job, PodFunctionBuild> {
        @Override
        public boolean isMet(DependentResource<Job, PodFunctionBuild> dependentResource, PodFunctionBuild primary, Context<PodFunctionBuild> context) {
            var phase = Optional.ofNullable(primary.getStatus())
                    .map(PodFunctionBuildStatus::getPhase)
                    .orElse(PodFunctionBuildStatus.Phase.Pending);
            return phase != PodFunctionBuildStatus.Phase.Failed && phase != PodFunctionBuildStatus.Phase.Succeeded;
        }
    }

    @Override
    protected Job desired(PodFunctionBuild primary, Context<PodFunctionBuild> context) {
        log.debug("Creating Job DR for build {}", primary.getMetadata());
        var podFunction = PodPoolResourceUtils.getReferencedPodFunction(primary, context.getClient()).orElseThrow();
        var podPool = PodPoolResourceUtils.getReferencedPodPool(podFunction, context.getClient()).orElseThrow();

        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels(PodFunctionBuildReconciler.SELECTOR, "true")
                .addNewOwnerReference()
                .withUid(primary.getMetadata().getUid())
                .withApiVersion(HasMetadata.getApiVersion(PodFunctionBuild.class))
                .withName(primary.getMetadata().getName())
                .withKind(HasMetadata.getKind(PodFunctionBuild.class))
                .withController(true)
                .withBlockOwnerDeletion(false)
                .endOwnerReference()
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
                .withServiceAccountName(podPool.getSpec().getBuilderPodServiceAccountName())
                .withRestartPolicy("Never")
                .addNewInitContainer()
                .withName("builder-init-container")
                .withImage("busybox:latest")
                .addAllToCommand(podFunction.getSpec().getInitCommands())
                .endInitContainer()
                .addNewContainer()
                .withName("build-container")
                .withImage(podPool.getSpec().getBuilderImage())
                .withCommand("sh", "/workspace/build.sh")
                .addToEnv(
                        new EnvVar("AGENT_CARD_JSON_PATH", "/workspace/agent_card.json", null),
                        new EnvVar("ARCHIVE_ROOT_PATH", "/archive", null),
                        new EnvVar("SOURCE_ARCHIVE_SUBPATH", primary.getSpec().getSourceArchiveSubPath(), null),
                        new EnvVar("DEPLOY_ARCHIVE_SUBPATH", PodPoolResourceUtils.computeDeployArchiveSubPath(primary), null),
                        new EnvVar("FUNCTION_NAME", podFunction.getMetadata().getName(), null),
                        new EnvVar("POD_POOL", podPool.getMetadata().getName(), null),
                        new EnvVar("NAMESPACE", primary.getMetadata().getNamespace(), null)
                )
                // mount source archive
                .addNewVolumeMount()
                .withMountPath("/archive")
                .withName("archive-volume")
                // scope to build folder of current build
                .withSubPath(primary.getMetadata().getName())
                .endVolumeMount()
                // mount build script
                .addNewVolumeMount()
                .withMountPath("/workspace")
                .withName("builder-script-volume")
                .endVolumeMount()
                .endContainer()
                // declare archive volume
                .addNewVolume()
                .withName("archive-volume")
                .withNewPersistentVolumeClaim()
                .withClaimName(podPool.getSpec().getArchivePvcName())
                .endPersistentVolumeClaim()
                .endVolume()
                // declare builder script volume
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

    private String jobName(PodFunctionBuild primary) {
        return String.format("build-%s", primary.getMetadata().getName());
    }
}
