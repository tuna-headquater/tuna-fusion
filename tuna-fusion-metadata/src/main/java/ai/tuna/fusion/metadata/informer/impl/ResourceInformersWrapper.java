package ai.tuna.fusion.metadata.informer.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author robinqu
 */
public class ResourceInformersWrapper<T extends HasMetadata> {

    public static final String ALL_NAMESPACE_IDENTIFIER = "*";

    private final Map<String, SharedIndexInformer<T>> sharedIndexInformers;
    @Getter
    private final Class<T> resourceType;
    private final boolean clusterScoped;

    public ResourceInformersWrapper(Map<String, SharedIndexInformer<T>> sharedIndexInformers, Class<T> resourceType) {
        this.sharedIndexInformers = sharedIndexInformers;
        this.resourceType = resourceType;
        this.clusterScoped = sharedIndexInformers.containsKey(ALL_NAMESPACE_IDENTIFIER);
    }

    public ResourceInformersWrapper(SharedIndexInformer<T> clusterScopedSharedIndexInformers, Class<T> resourceType) {
        this(Map.of(ALL_NAMESPACE_IDENTIFIER, clusterScopedSharedIndexInformers), resourceType);
    }

    public Optional<SharedIndexInformer<T>> getSharedIndexInformer(String namespace) {
        if (clusterScoped) {
            return Optional.ofNullable(sharedIndexInformers.get(ALL_NAMESPACE_IDENTIFIER));
        }
        return Optional.ofNullable(sharedIndexInformers.get(namespace));
    }

    public Collection<SharedIndexInformer<T>> getSharedIndexInformers() {
        return sharedIndexInformers.values();
    }

    public void addEventHandler(ResourceEventHandler<? super T> handler) {
        sharedIndexInformers.values().forEach(indexInformer -> indexInformer.addEventHandler(handler));
    }

    public void removeEventHandler(ResourceEventHandler<? super T> handler) {
        sharedIndexInformers.values().forEach(indexInformer -> indexInformer.removeEventHandler(handler));
    }

    @SneakyThrows
    void start() {
        startAsync().get(10, TimeUnit.SECONDS);
    }

    public synchronized Future<Void> startAsync() {
        List<CompletableFuture<Void>> startInformerTasks = new ArrayList<>();
        for (SharedIndexInformer<?> informer : sharedIndexInformers.values()) {
            CompletableFuture<Void> future = informer.start().toCompletableFuture();
            startInformerTasks.add(future);
        }
        return CompletableFuture.allOf(startInformerTasks.toArray(new CompletableFuture[] {}));
    }

    void stop() {
        sharedIndexInformers.values().forEach(SharedIndexInformer::stop);
    }

}
