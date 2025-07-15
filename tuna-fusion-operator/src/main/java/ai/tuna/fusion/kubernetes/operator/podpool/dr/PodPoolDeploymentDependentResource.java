package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodPoolReconciler;
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
import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.getPodPoolDeploymentName;

/**
 * @author robinqu
 */
@Slf4j
@KubernetesDependent(informer = @Informer(labelSelector = PodPoolReconciler.SELECTOR))
public class PodPoolDeploymentDependentResource extends CRUDKubernetesDependentResource<Deployment, PodPool> {

    @Override
    protected Deployment desired(PodPool primary, Context<PodPool> context) {
        var selectorLabels = computeGenericPodSelectors(primary);
        var deployLabels = PodPoolResourceUtils.computeDeployLabels(primary);
        var poolSize = primary.getSpec().getPoolSize();

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
                .withSubPath(primary.getMetadata().getName())
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
                        .withName("RUNTIME_SERVICE_PORT")
                        .withValue(String.valueOf(PodPool.DEFAULT_RUNTIME_SERVICE_PORT))
                        .build(),
                new EnvVarBuilder()
                        .withName("ARCHIVE_ROOT_PATH")
                        .withValue(PodFunctionBuild.ARCHIVE_ROOT_PATH.toString())
                        .build()
        ));

        return new DeploymentBuilder()
                .withNewMetadata()
                .addToLabels(PodPoolReconciler.SELECTOR, "true")
                .withName(getPodPoolDeploymentName(primary))
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
                .withServiceAccountName(podPool.getSpec().getRuntimePodServiceAccountName())
                .addNewContainer()
                .withName(podPool.getMetadata().getName() + "-container")
                .withImage(podPool.getSpec().getRuntimeImage())
                .endContainer()
                .build();
    }

}
