package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import lombok.extern.slf4j.Slf4j;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.computeServiceLabels;

/**
 * @author robinqu
 */
@Slf4j
@KubernetesDependent(informer = @Informer(labelSelector = PodPool.DR_SELECTOR))
public class PodPoolServiceDependentResource extends CRUDKubernetesDependentResource<Service, PodPool> {
    @Override
    protected Service desired(PodPool primary, Context<PodPool> context) {
        return new ServiceBuilder()
                .withNewMetadata()
                .addToLabels(PodPool.DR_SELECTOR, "true")
                .withName(PodPoolResourceUtils.computePodPoolServiceName( primary))
                .addNewOwnerReference()
                .withUid(primary.getMetadata().getUid())
                .withApiVersion(HasMetadata.getApiVersion(PodPool.class))
                .withName(primary.getMetadata().getName())
                .withKind(HasMetadata.getKind(PodPool.class))
                .withController(true)
                .withBlockOwnerDeletion(false)
                .endOwnerReference()
                .withNamespace(primary.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withClusterIP("None")
                .withSelector(computeServiceLabels(primary))
                .addNewPort()
                .withProtocol("TCP")
                .withPort(80)
                .withTargetPort(new IntOrString(PodPool.DEFAULT_RUNTIME_SERVICE_PORT))
                .withName("http")
                .endPort()
                .endSpec()
                .build();
    }
}
