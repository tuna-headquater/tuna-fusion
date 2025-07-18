package ai.tuna.fusion.metadata.informer;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.Optional;

/**
 * @author robinqu
 */
public interface PodPoolResources {
    SharedIndexInformer<PodPool> podPool();
    SharedIndexInformer<Pod> pod();
    SharedIndexInformer<Service> service();
    SharedIndexInformer<PodFunction> podFunction();
    SharedIndexInformer<PodFunctionBuild> podFunctionBuild();

    Optional<PodPool> queryPodPool(String namespace, String podPoolName);
    Optional<Pod> queryPod(String namespace, String podName);
    Optional<PodFunction> queryPodFunction(String namespace, String podFunctionName);
    Optional<PodFunctionBuild> queryPodFunctionBuild(String namespace, String podFunctionBuildName);
    Optional<Service> queryPodPoolService(String namespace, String podPoolName);

    KubernetesClient getKubernetesClient();
}
