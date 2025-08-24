package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.common.ConfigurationUtils;
import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodFunctionBuildReconciler;
import ai.tuna.fusion.kubernetes.operator.support.impl.FunctionBuildPodBuilderFileAssets;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.*;


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
        var sourcePatchPath = PodFunctionBuild.SOURCE_PATCH_PATH.toString();
        var builderImage = Optional.ofNullable(primary.getSpec().getEnvironmentOverrides())
                .map(PodFunctionBuildSpec.EnvironmentOverrides::getBuilderImage)
                .or(()-> Optional.ofNullable(podPool.getSpec().getBuilderImage()))
                .or(()-> ConfigurationUtils.getStaticValue("operator.builderImage"))
                .orElseThrow();
        var fileAssetsBuilder = new FunctionBuildPodBuilderFileAssets(primary, podFunction, podPool);
        var ns = podFunction.getMetadata().getNamespace();
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
                .withNewSpec()
                .withServiceAccountName(builderServiceAccountName(primary, podPool))
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName("build-container")
                .withImage(builderImage)
                .addAllToEnv(fileAssetsBuilder.fileAssetsEnvVars())
                .addToEnv(
                        new EnvVar("NAMESPACE", ns, null),
                        new EnvVar("WORKSPACE_ROOT_PATH", workspacePath, null),
                        new EnvVar("ARCHIVE_ROOT_PATH", archivePath, null),
                        new EnvVar("DEPLOY_ARCHIVE_PATH", PodPoolResourceUtils.computeDeployArchivePath(primary), null),
                        new EnvVar("SOURCE_ARCHIVE_PATH", PodPoolResourceUtils.computeSourceArchivePath(primary), null),
                        new EnvVar("FUNCTION_NAME", podFunction.getMetadata().getName(), null),
                        new EnvVar("POD_POOL", podPool.getMetadata().getName(), null),
                        new EnvVar("FUNCTION_BUILD_NAME", primary.getMetadata().getName(), null),
                        new EnvVar("FUNCTION_BUILD_UID", primary.getMetadata().getUid(), null)
                )
                // mount configmaps for PF
                .addAllToVolumeMounts(
                        Optional.ofNullable(podFunction.getSpec().getConfigmaps()).orElse(Collections.emptyList())
                                .stream().map(configmapReference -> new VolumeMountBuilder()
                                .withName(configmapVolumeName(configmapReference.getName()))
                                .withReadOnly(true)
                                .withMountPath("/configmaps/%s/%s".formatted(ns, configmapReference.getName()))
                                .build()
                        ).toList()
                )
                // mount secrets for PF
                .addAllToVolumeMounts(
                        Optional.ofNullable(podFunction.getSpec().getSecrets()).orElse(Collections.emptyList())
                        .stream().map(secretReference -> new VolumeMountBuilder()
                        .withReadOnly(true)
                        .withName(secretVolumeName(secretReference.getName()))
                        .withMountPath("/secrets/%s/%s".formatted(ns, secretReference.getName()))
                        .build()).toList())
                // mount source archive
                .addNewVolumeMount()
                .withMountPath(archivePath)
                .withName("archive-volume")
                .endVolumeMount()
                // mount build script
                .addNewVolumeMount()
                .withMountPath(workspacePath)
                .withName("workspace-volume")
                .endVolumeMount()
                .addNewVolumeMount()
                .withMountPath(sourcePatchPath)
                .withName("source-patch-volume")
                .endVolumeMount()
                .endContainer()
                // volumes for configmap in PF
                .addAllToVolumes(
                        Optional.ofNullable(podFunction.getSpec().getConfigmaps()).orElse(Collections.emptyList())
                                .stream().map(configmapReference -> new VolumeBuilder()
                                .withName(configmapVolumeName(configmapReference.getName()))
                                .withNewConfigMap()
                                .withName(configmapReference.getName())
                                .withOptional(true)
                                .endConfigMap()
                                .build()
                        ).toList()
                )
                // volumes for secrets in PF
                .addAllToVolumes(
                        Optional.ofNullable(podFunction.getSpec().getSecrets()).orElse(Collections.emptyList())
                                .stream().map(secretReference ->
                                new VolumeBuilder()
                                        .withName(secretVolumeName(secretReference.getName()))
                                        .withNewSecret()
                                        .withSecretName(secretReference.getName())
                                        .withOptional(true)
                                        .endSecret()
                                        .build()
                                        ).toList()
                )
                // declare archive volume
                .addNewVolume()
                .withName("archive-volume")
                .withNewPersistentVolumeClaim()
                .withClaimName(archivePvcName(podPool))
                .endPersistentVolumeClaim()
                .endVolume()
                // declare builder script volume
                .addNewVolume()
                .withName("workspace-volume")
                .withNewConfigMap()
                .withName(workspaceConfigMapName(primary))
                .withOptional(false)
                .endConfigMap()
                .endVolume()
                .addNewVolume()
                .withName("source-patch-volume")
                .withNewConfigMap()
                .withOptional(false)
                .withName(sourcePathConfigMapName(primary))
                .endConfigMap()
                .endVolume()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private String secretVolumeName(String secretName) {
        return secretName + "-secret-volume";
    }

    private String configmapVolumeName(String configmapName) {
        return configmapName + "-configmap-volume";
    }

    /**
     * PVC for local shared archive. Use the one specified in pod pool spec, or the one specified in env var.
     */
    private String archivePvcName(PodPool podPool) {
        return Optional.ofNullable(podPool.getSpec().getArchivePvcName())
                .or(()-> ConfigurationUtils.getStaticValue("operator.sharedArchivePvcName"))
                .orElseThrow(()-> new IllegalArgumentException("Archive PVC name not found for PodPool " + podPool.getMetadata().getName()));
    }

    /**
     * Service account name for builder pod. Use the one specified in pod pool spec, or the one specified in `environmentOverrides` of PodFunctionBuild, or the one specified in system properties.
     */
    private String builderServiceAccountName(PodFunctionBuild primary, PodPool podPool) {
        return Optional.ofNullable(primary.getSpec().getEnvironmentOverrides())
                .map(PodFunctionBuildSpec.EnvironmentOverrides::getServiceAccountName)
                .orElseGet(()-> Optional.ofNullable(podPool.getSpec().getBuilderPodServiceAccountName()).orElse(System.getProperty("operator.builderServiceAccountName")));
    }
}
