package ai.tuna.fusion.kubernetes.operator.driver.podpool.dr;

import ai.tuna.fusion.kubernetes.operator.driver.podpool.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.AgentEnvironmentSpec;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

import java.util.Optional;

import static ai.tuna.fusion.kubernetes.operator.driver.podpool.PodPoolResourceUtils.computePodSelectors;
import static ai.tuna.fusion.kubernetes.operator.driver.podpool.PodPoolResourceUtils.getPodPoolDeploymentName;

/**
 * @author robinqu
 */
public class PodPoolDeploymentDependentResource extends CRUDKubernetesDependentResource<Deployment, AgentEnvironment> {


    @Override
    protected Deployment desired(AgentEnvironment primary, Context<AgentEnvironment> context) {
        var selectorLabels = computePodSelectors(primary);
        var deployLabels = PodPoolResourceUtils.computeDeployLabels(primary);
        var poolOptions = Optional.ofNullable(primary.getSpec()).map(AgentEnvironmentSpec::getDriver).map(AgentEnvironmentSpec.DriverSpec::getPodPoolSpec);
        var podSpec = poolOptions.map(AgentEnvironmentSpec.DriverSpec.PodPoolSpec::getPodSpec).orElseThrow();
        var poolSize = poolOptions.map(AgentEnvironmentSpec.DriverSpec.PodPoolSpec::getPoolSize).orElse(3);
        podSpec.getVolumes().add(new VolumeBuilder()
                .withName("archive")
                .withNewPersistentVolumeClaim()
                .withClaimName(PodPoolResourceUtils.getArchivePvcName(primary))
                .endPersistentVolumeClaim()
                .build());
        podSpec.getContainers().getFirst().getVolumeMounts().add(new VolumeMountBuilder()
                .withName("archive")
                .withSubPath(primary.getMetadata().getName())
                .withMountPath("/archive")
                .build());

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(getPodPoolDeploymentName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addNewOwnerReference()
                .withName(primary.getMetadata().getName())
                .withKind(HasMetadata.getKind(AgentEnvironment.class))
                .withApiVersion(HasMetadata.getApiVersion(AgentEnvironment.class))
                .withUid(primary.getMetadata().getUid())
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
