package ai.tuna.fusion.metadata.crd;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author robinqu
 */
public class ResourceUtils {
    public static <Resource extends HasMetadata> boolean deleteResource(KubernetesClient client, String namespace, String name, Class<Resource> clazz, int timeoutInSeconds) {
        var result = client.resources(clazz)
                .inNamespace( namespace)
                .withName(name)
                .withTimeout(timeoutInSeconds, TimeUnit.SECONDS)
                .delete();
        return result != null && !result.isEmpty();
    }

    public static  <Resource extends HasMetadata> Optional<Resource> getKubernetesResource(KubernetesClient kubernetesClient, String name, String namespace, Class<Resource> clazz) {
        return Optional.ofNullable(kubernetesClient.resources(clazz)
                .inNamespace(namespace)
                .withName(name)
                .get());
    }


    public static Optional<Pod> getPod(KubernetesClient client, String ns, String podName) {
        return Optional.ofNullable(client.pods()
                .inNamespace(ns)
                .withName(podName)
                .get());
    }


    public static Optional<Job> getBatchJob(KubernetesClient client, String jobName, String ns) {
        return Optional.ofNullable(client.batch()
                .v1()
                .jobs()
                .inNamespace(ns)
                .withName(jobName)
                .get());
    }

    public static <Resource extends HasMetadata> Optional<Resource> getResourceFromInformer(SharedIndexInformer<Resource> informer, String ns, String name) {
        return Optional.ofNullable(informer.getStore().getByKey(Cache.namespaceKeyFunc(ns, name)));
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static final String POD_HTTP_URL = "http://%s:%s/%s";

    public static String getPodUri(Pod pod) {
        return getPodUri(pod,"");
    }
    public static String getPodUri(Pod pod, String subPath) {
        var port = pod.getSpec().getContainers().getFirst().getPorts().getFirst().getContainerPort();
        var ip = pod.getStatus().getPodIP();
        if (subPath.startsWith("/")) {
            subPath = subPath.substring(1);
        }
        return String.format(POD_HTTP_URL, ip, port, subPath);
    }

    public static <ChildResource extends HasMetadata, OwnerResource extends HasMetadata> Optional<String> getMatchedOwnerReferenceResourceName(ChildResource resource, Class<OwnerResource> ownerClass) {
        return resource.getMetadata().getOwnerReferences()
                .stream().filter(ownerReference -> Strings.CS.equals(ownerReference.getKind(), HasMetadata.getKind(ownerClass)) && Strings.CS.equals(ownerReference.getApiVersion(), HasMetadata.getApiVersion(ownerClass)))
                .map(OwnerReference::getName)
                .findAny();
    }

    public static String computeResourceMetaKey(HasMetadata resource) {
        return "%s/%s".formatted(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    public static Pair<String, String> parseResourceMetaKey(String key) {
        var split = key.split("/");
        return Pair.of(split[0], split[1]);
    }

    public static boolean hasOwnerReference(HasMetadata resource, HasMetadata owner) {
        return resource.getMetadata().getOwnerReferences()
                .stream().anyMatch(ownerReference -> Strings.CS.equals(ownerReference.getKind(), owner.getKind()) && Strings.CS.equals(ownerReference.getApiVersion(), owner.getApiVersion()) && Strings.CS.equals(ownerReference.getName(), owner.getMetadata().getName()));
    }

    public static void addOwnerReference(HasMetadata resource, HasMetadata owner, boolean controller) {
        if (!hasOwnerReference(resource, owner)) {
            resource.getMetadata().getOwnerReferences().add(new OwnerReference(
                    owner.getApiVersion(),
                    false,
                    true,
                    owner.getKind(),
                    owner.getMetadata().getName(),
                    owner.getMetadata().getUid()
            ));
        }
    }

}
