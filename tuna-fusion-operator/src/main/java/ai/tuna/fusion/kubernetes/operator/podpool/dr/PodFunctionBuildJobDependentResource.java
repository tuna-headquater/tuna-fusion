package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodFunctionBuildReconciler;
import ai.tuna.fusion.kubernetes.operator.support.impl.FunctionBuildPodInitContainerCommand;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.computeJobName;


/**
 * @author robinqu
 */
@KubernetesDependent(informer = @Informer(labelSelector = PodFunctionBuildReconciler.SELECTOR))
@Slf4j
public class PodFunctionBuildJobDependentResource extends KubernetesDependentResource<Job, PodFunctionBuild> implements GarbageCollected<PodFunctionBuild>, Creator<Job, PodFunctionBuild> {

    @Override
    protected Job desired(PodFunctionBuild primary, Context<PodFunctionBuild> context) {
        log.info("[desired] Resolving Job DR for build {}", primary.getMetadata());
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
                .withName(computeJobName(primary))
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
                // disallow retry
                .withBackoffLimit(0)
                .withCompletions(1)
                .withActiveDeadlineSeconds(60 * 10L)
                .withNewTemplate()
//                .withNewMetadata()
//                .withName(computeJobPodName(primary))
//                .withNamespace(primary.getMetadata().getNamespace())
//                .endMetadata()
                .withNewSpec()
                .withServiceAccountName(serviceAccountName)
                .withRestartPolicy("Never")
                .addNewInitContainer()
                .withName("builder-init-container")
                .withImage("busybox:1.36.1-glibc")
                .addAllToCommand(initCommand.renderInitCommand())
                .addNewVolumeMount()
                .withMountPath(archivePath)
                .withName("archive-volume")
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
}
