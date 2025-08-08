package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.common.ConfigurationUtils;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.computeGenericPodSelectors;
import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.computePodPoolDeploymentName;
import static ai.tuna.fusion.metadata.crd.podpool.PodPool.DEFAULT_POOL_SIZE;

/**
 * @author robinqu
 */
@Slf4j
@KubernetesDependent(informer = @Informer(labelSelector = PodPool.DR_SELECTOR))
public class PodPoolDeploymentDependentResource extends CRUDKubernetesDependentResource<Deployment, PodPool> {

    @Override
    protected Deployment desired(PodPool primary, Context<PodPool> context) {
        log.debug("[desired Configure Deployment for PodPool: {}/{}", primary.getMetadata().getNamespace(), primary.getMetadata().getName());
        var selectorLabels = computeGenericPodSelectors(primary);
        var deployLabels = PodPoolResourceUtils.computeDeployLabels(primary);
        var poolSize = Optional.ofNullable(primary.getSpec().getPoolSize())
                .orElse(DEFAULT_POOL_SIZE);

        var podSpec = Optional.ofNullable(primary.getSpec().getRuntimePodSpec()).map(templatePodSpec -> templatePodSpec.toBuilder().build()).orElseGet(()-> podSpec(primary));

        podSpec.getVolumes().add(new VolumeBuilder()
                .withName("archive-volume")
                .withNewPersistentVolumeClaim()
                .withClaimName(primary.getSpec().getArchivePvcName())
                .endPersistentVolumeClaim()
                .build());

        var container = podSpec.getContainers().getFirst();
        container.getVolumeMounts().add(new VolumeMountBuilder()
                .withName("archive-volume")
                .withReadOnly(true)
                .withMountPath(PodFunctionBuild.ARCHIVE_ROOT_PATH.toString())
                .build());
        // clear ports for safety reasons
        container.getPorts().clear();
        // add http port
        container.getPorts().add(new ContainerPortBuilder()
                        .withContainerPort(PodPool.DEFAULT_RUNTIME_SERVICE_PORT)
                        .withName("http")
                        .withProtocol("TCP")
                .build());
        container.getEnv().addAll(Arrays.asList(
                new EnvVarBuilder()
                        .withName("MANAGED_BY_POD_POOL")
                        .withValue("true")
                        .build(),
                new EnvVarBuilder()
                        .withName("RUNTIME_SERVICE_PORT")
                        .withValue(String.valueOf(PodPool.DEFAULT_RUNTIME_SERVICE_PORT))
                        .build(),
                new EnvVarBuilder()
                        .withName("ARCHIVE_ROOT_PATH")
                        .withValue(PodFunctionBuild.ARCHIVE_ROOT_PATH.toString())
                        .build()
        ));
        podSpec.setSubdomain(PodPoolResourceUtils.computePodPoolServiceName(primary));

        return new DeploymentBuilder()
                .withNewMetadata()
                .addToLabels(PodPool.DR_SELECTOR, "true")
                .withName(computePodPoolDeploymentName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addNewOwnerReference()
                .withUid(primary.getMetadata().getUid())
                .withApiVersion(HasMetadata.getApiVersion(PodPool.class))
                .withName(primary.getMetadata().getName())
                .withKind(HasMetadata.getKind(PodPool.class))
                .withController(true)
                .withBlockOwnerDeletion(false)
                .endOwnerReference()
                .addToLabels(deployLabels)
                .endMetadata()
                .withNewSpec()
                .withReplicas(poolSize)
                .withNewSelector()
                .addToMatchLabels(selectorLabels)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(selectorLabels)
                .endMetadata()
                .withSpec(podSpec)
                .endTemplate()
                .endSpec()
                .build();
    }

    private PodSpec podSpec(PodPool podPool) {
        return new PodSpecBuilder()
                .withServiceAccountName(serviceAccountName(podPool))
                .addNewContainer()
                .withName(podPool.getMetadata().getName() + "-container")
                .withImage(podPool.getSpec().getRuntimeImage())
                .withNewReadinessProbe()
                .withInitialDelaySeconds(1)
                .withPeriodSeconds(5)
                .withNewHttpGet()
                .withNewPort()
                .withValue(PodPool.DEFAULT_RUNTIME_SERVICE_PORT)
                .endPort()
                .withScheme("HTTP")
                .withPath("/health")
                .endHttpGet()
                .endReadinessProbe()
                .withNewLivenessProbe()
                .withInitialDelaySeconds(3)
                .withPeriodSeconds(10)
                .withNewHttpGet()
                .withNewPort()
                .withValue(PodPool.DEFAULT_RUNTIME_SERVICE_PORT)
                .endPort()
                .withScheme("HTTP")
                .withPath("/health")
                .endHttpGet()
                .endLivenessProbe()
                .endContainer()
                .build();
    }

    /**
     * Get the service account name to use for the pod pool
     */
    private String serviceAccountName(PodPool podPool) {
        return Optional.ofNullable(podPool.getSpec().getRuntimePodServiceAccountName())
                .orElse(ConfigurationUtils.getStaticValue("operator.runtimePodServiceAccountName", "default"));
    }

}
