package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author robinqu
 */
public class DefaultPodPoolResources extends AbstractResourceOperations implements PodPoolResources {
    private final SharedIndexInformer<PodPool> podPoolSharedIndexInformer = createInformer(PodPool.class);
    private final SharedIndexInformer<Pod> podSharedIndexInformer = createInformer(Pod.class, PodPool.DR_SELECTOR);
    private final SharedIndexInformer<Service> serviceSharedIndexInformer = createInformer(Service.class, PodPool.DR_SELECTOR);
    private final SharedIndexInformer<PodFunction> podFunctionSharedIndexInformer = createInformer(PodFunction.class);

    public DefaultPodPoolResources(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    public SharedIndexInformer<PodPool> podPool() {
        return podPoolSharedIndexInformer;
    }

    @Override
    public SharedIndexInformer<Pod> pod() {
        return podSharedIndexInformer;
    }

    @Override
    public SharedIndexInformer<Service> service() {
        return serviceSharedIndexInformer;
    }

    @Override
    public SharedIndexInformer<PodFunction> podFunction() {
        return podFunctionSharedIndexInformer;
    }

    @Override
    public Optional<PodPool> queryPodPool(String namespace, String podPoolName) {
        return ResourceUtils.getResourceFromInformer(podPoolSharedIndexInformer, namespace, podPoolName);
    }

    @Override
    public Optional<Pod> queryPod(String namespace, String podName) {
        return ResourceUtils.getResourceFromInformer(podSharedIndexInformer, namespace, podName);
    }

    @Override
    public Optional<PodFunction> queryPodFunction(String namespace, String podFunctionName) {
        return ResourceUtils.getResourceFromInformer(podFunctionSharedIndexInformer, namespace, podFunctionName);
    }

    @Override
    public Optional<Service> queryPodPoolService(String namespace, String podPoolName) {
        return ResourceUtils.getResourceFromInformer(serviceSharedIndexInformer, namespace, podPoolName);
    }

    @Override
    public boolean isRunning() {
        return Stream.of(
                podPoolSharedIndexInformer,
                podSharedIndexInformer,
                serviceSharedIndexInformer,
                podFunctionSharedIndexInformer
        ).allMatch(SharedIndexInformer::isRunning);
    }
}
