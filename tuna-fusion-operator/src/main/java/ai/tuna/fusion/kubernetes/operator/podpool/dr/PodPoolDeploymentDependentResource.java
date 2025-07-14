package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.kubernetes.operator.podpool.PodPoolResourceUtils;
import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodPoolReconciler;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

import static ai.tuna.fusion.kubernetes.operator.podpool.PodPoolResourceUtils.computeGenericPodSelectors;
import static ai.tuna.fusion.kubernetes.operator.podpool.PodPoolResourceUtils.getPodPoolDeploymentName;

/**
 * @author robinqu
 */
@Slf4j
@KubernetesDependent(informer = @Informer(labelSelector = PodPoolReconciler.SELECTOR))
public class PodPoolDeploymentDependentResource extends CRUDKubernetesDependentResource<Deployment, PodPool> {

    static final private PodSpec TEMPLATE_POD_SPEC = new PodSpec();

    @Override
    protected Deployment desired(PodPool primary, Context<PodPool> context) {
        var selectorLabels = computeGenericPodSelectors(primary);
        var deployLabels = PodPoolResourceUtils.computeDeployLabels(primary);
        var poolSize = primary.getSpec().getPoolSize();

        var podSpec = Optional.ofNullable(primary.getSpec().getRuntimePodSpec()).map(templatePodSpec -> templatePodSpec.toBuilder().build()).orElse(TEMPLATE_POD_SPEC);

        podSpec.getContainers().getFirst().setImage(primary.getSpec().getRuntimeImage());
        if (StringUtils.isNoneBlank(primary.getSpec().getRuntimePodServiceAccountName())) {
            podSpec.setServiceAccountName(primary.getSpec().getRuntimePodServiceAccountName());
        }

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
                .withMountPath("/archive")
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
                        .withValue("/archive")
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

}
