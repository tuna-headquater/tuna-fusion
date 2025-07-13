package ai.tuna.fusion.metadata.crd;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;

import java.util.Optional;

/**
 * @author robinqu
 */
public class ResourceUtils {
    public static <Resource extends HasMetadata> boolean deleteResource(KubernetesClient client, String namespace, String name, Class<Resource> clazz) {
        var result = client.resources(clazz)
                .inNamespace( namespace)
                .withName(name)
                .delete();
        return result != null && !result.isEmpty();
    }

    public static  <Resource extends HasMetadata> Resource getKubernetesResource(KubernetesClient kubernetesClient, String name, String namespace, Class<Resource> clazz) {
        return kubernetesClient.resources(clazz)
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static <Resource extends HasMetadata> Optional<Resource> getResourceFromInformer(SharedIndexInformer<Resource> informer, String ns, String name) {
        return Optional.ofNullable(informer.getStore().getByKey(Cache.namespaceKeyFunc(ns, name)));
    }


}
