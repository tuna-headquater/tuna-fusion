package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * @author robinqu
 */
@Slf4j
public class DefaultPodPoolResources extends AbstractResourceOperations implements PodPoolResources {

    public DefaultPodPoolResources(KubernetesClient kubernetesClient, InformerProperties informerProperties) {
        super(kubernetesClient, informerProperties);
    }

    @Override
    protected void configureInformers() {
        prepareInformers(PodPool.class);
        prepareInformers(Pod.class);
        prepareInformers(Service.class);
        prepareInformers(PodFunction.class);
        prepareInformers(PodFunctionBuild.class);
    }


    @Override
    public ResourceInformersWrapper<PodPool> podPool() {
        return getInformersWrapper(PodPool.class).orElseThrow();
    }

    @Override
    public ResourceInformersWrapper<Pod> pod() {
        return getInformersWrapper(Pod.class).orElseThrow();
    }

    @Override
    public ResourceInformersWrapper<Service> service() {
        return getInformersWrapper(Service.class).orElseThrow();
    }

    @Override
    public ResourceInformersWrapper<PodFunction> podFunction() {
        return getInformersWrapper(PodFunction.class).orElseThrow();
    }

    @Override
    public ResourceInformersWrapper<PodFunctionBuild> podFunctionBuild() {
        return getInformersWrapper(PodFunctionBuild.class).orElseThrow();
    }

    @Override
    public Optional<PodPool> queryPodPool(String namespace, String podPoolName) {
        log.debug("[queryPodPool] {}/{}", namespace, podPoolName);
        return ResourceUtils.getResourceFromInformer(
                getSharedInformer(PodPool.class, namespace).orElseThrow(),
                namespace,
                podPoolName
        );
    }

    @Override
    public Optional<Pod> queryPod(String namespace, String podName) {
        log.debug("[queryPod] {}/{}", namespace, podName);
        return ResourceUtils.getResourceFromInformer(
                getSharedInformer(Pod.class, namespace).orElseThrow(),
                namespace,
                podName
        );
    }

    @Override
    public Optional<PodFunction> queryPodFunction(String namespace, String podFunctionName) {
        log.debug("[queryPodFunction] {}/{}", namespace, podFunctionName);
        return ResourceUtils.getResourceFromInformer(
                getSharedInformer(PodFunction.class, namespace).orElseThrow(),
                namespace,
                podFunctionName
        );
    }

    @Override
    public Optional<Service> queryPodPoolService(String namespace, String svcName) {
        log.debug("[queryPodPoolService] {}/{}", namespace, svcName);
        return ResourceUtils.getResourceFromInformer(
                getSharedInformer(Service.class, namespace).orElseThrow(),
                namespace,
                svcName
        );
    }

    @Override
    public Optional<PodFunctionBuild> queryPodFunctionBuild(String namespace, String podFunctionBuildName) {
        log.debug("[queryPodFunctionBuild] {}/{}", namespace, podFunctionBuildName);
        return ResourceUtils.getResourceFromInformer(
                getSharedInformer(PodFunctionBuild.class, namespace).orElseThrow(),
                namespace,
                podFunctionBuildName
        );
    }
}
