package ai.tuna.fusion.kubernetes.operator.driver.podpool.dr;

import ai.tuna.fusion.kubernetes.operator.driver.podpool.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.AgentEnvironmentSpec;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

import java.util.Map;
import java.util.Optional;

/**
 * @author robinqu
 */
public class PodPoolArchivePVCDependentResource extends CRUDKubernetesDependentResource<PersistentVolumeClaim, AgentEnvironment> {

    public static final String DEFAULT_PVC_CAPACITY = "10Gi";

    @Override
    protected PersistentVolumeClaim desired(AgentEnvironment primary, Context<AgentEnvironment> context) {
        var poolSpec = Optional.of(primary.getSpec())
                .map(AgentEnvironmentSpec::getDriver)
                .map(AgentEnvironmentSpec.DriverSpec::getPodPoolSpec);

        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(PodPoolResourceUtils.getArchivePvcName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteMany")
                .withNewResources()
                .withRequests(
                        Map.of(
                        "storage",
                        new Quantity(
                                poolSpec
                                        .map(AgentEnvironmentSpec.DriverSpec.PodPoolSpec::getArchivePvcCapacity)
                                        .orElse(DEFAULT_PVC_CAPACITY))
                        )
                )
                .endResources()
                .endSpec()
                .build();
    }
}
