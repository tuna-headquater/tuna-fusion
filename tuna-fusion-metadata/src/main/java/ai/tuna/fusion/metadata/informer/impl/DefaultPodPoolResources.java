package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author robinqu
 */
public class DefaultPodPoolResources extends AbstractResourceOperations implements PodPoolResources {
    private SharedIndexInformer<PodPool> podPoolSharedIndexInformer;
    private SharedIndexInformer<Pod> podSharedIndexInformer;
    private SharedIndexInformer<Service> serviceSharedIndexInformer;
    private SharedIndexInformer<PodFunction> podFunctionSharedIndexInformer;
    private SharedIndexInformer<PodFunctionBuild> podFunctionBuildSharedIndexInformer;

    public DefaultPodPoolResources(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected List<SharedIndexInformer<?>> createInformers() {
        this.podPoolSharedIndexInformer = createInformer(PodPool.class);
        this.podSharedIndexInformer = createInformer(Pod.class);
        this.serviceSharedIndexInformer = createInformer(Service.class);
        this.podFunctionSharedIndexInformer = createInformer(PodFunction.class);
        this.podFunctionBuildSharedIndexInformer = createInformer(PodFunctionBuild.class);
        return List.of(
                podPoolSharedIndexInformer,
                podSharedIndexInformer,
                serviceSharedIndexInformer,
                podFunctionSharedIndexInformer,
                podFunctionBuildSharedIndexInformer
        );
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
    public SharedIndexInformer<PodFunctionBuild> podFunctionBuild() {
        return podFunctionBuildSharedIndexInformer;
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
    public Optional<PodFunctionBuild> queryPodFunctionBuild(String namespace, String podFunctionBuildName) {
        return ResourceUtils.getResourceFromInformer(podFunctionBuildSharedIndexInformer, namespace, podFunctionBuildName);
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
