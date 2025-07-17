package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodFunctionBuildReconciler;
import ai.tuna.fusion.kubernetes.operator.support.impl.FunctionBuildPodInitContainerCommand;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
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
        log.debug("Resolving Job DR for build {}", primary.getMetadata());
        var podFunction = PodPoolResourceUtils.getReferencedPodFunction(primary, context.getClient()).orElseThrow(()-> new IllegalArgumentException("PodFunction not found for PodFunctionBuild " + primary.getMetadata().getName()));
        var podPool = PodPoolResourceUtils.getReferencedPodPool(podFunction, context.getClient()).orElseThrow(()-> new IllegalArgumentException("PodPool not found for PodFunction " + podFunction.getMetadata().getName()));
        var archivePath = PodFunctionBuild.ARCHIVE_ROOT_PATH.toString();
        var workspacePath = PodFunctionBuild.WORKSPACE_ROOT_PATH.toString();
        var builderImage = Optional.ofNullable(primary.getSpec().getEnvironmentOverrides())
                .map(PodFunctionBuildSpec.EnvironmentOverrides::getBuilderImage)
                .orElse(podPool.getSpec().getBuilderImage());
        var serviceAccountName = Optional.ofNullable(primary.getSpec().getEnvironmentOverrides())
                .map(PodFunctionBuildSpec.EnvironmentOverrides::getServiceAccountName)
                .orElse(podPool.getSpec().getBuilderPodServiceAccountName());

        var initCommand = new FunctionBuildPodInitContainerCommand(primary, podFunction, podPool);

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
                .withGenerateName(primary.getMetadata().getName() + "-")
                .withNamespace(primary.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withServiceAccountName(serviceAccountName)
                .withRestartPolicy("Never")
                .addNewInitContainer()
                .withName("builder-init-container")
                .withImage("busybox:latest")
                .addAllToCommand(initCommand.renderInitCommand())
                .addNewVolumeMount()
                .withMountPath(archivePath)
                .withName("archive-volume")
                // scope to build folder of current build
                .withSubPath(primary.getMetadata().getName())
                .endVolumeMount()
                // mount build script
                .addNewVolumeMount()
                .withMountPath(workspacePath)
                .withName("builder-script-volume")
                .endVolumeMount()
                .endInitContainer()
                .addNewContainer()
                .withName("build-container")
                .withImage(builderImage)
                .addAllToEnv(initCommand.renderFileAssetsEnvVars())
                .addToEnv(
                        new EnvVar("WORKSPACE_ROOT_PATH", workspacePath, null),
                        new EnvVar("ARCHIVE_ROOT_PATH", archivePath, null),
                        new EnvVar("DEPLOY_ARCHIVE_PATH", PodPoolResourceUtils.computeDeployArchivePath(primary), null),
                        new EnvVar("SOURCE_ARCHIVE_PATH", PodPoolResourceUtils.computeSourceArchivePath(primary), null),
                        new EnvVar("FUNCTION_NAME", podFunction.getMetadata().getName(), null),
                        new EnvVar("POD_POOL", podPool.getMetadata().getName(), null),
                        new EnvVar("FUNCTION_BUILD_NAME", primary.getMetadata().getName(), null),
                        new EnvVar("FUNCTION_BUILD_UID", primary.getMetadata().getUid(), null),
                        new EnvVar("NAMESPACE", primary.getMetadata().getNamespace(), null)
                )
                // mount source archive
                .addNewVolumeMount()
                .withMountPath(archivePath)
                .withName("archive-volume")
                // scope to build folder of current build
                .withSubPath(primary.getMetadata().getName())
                .endVolumeMount()
                // mount build script
                .addNewVolumeMount()
                .withMountPath(workspacePath)
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
