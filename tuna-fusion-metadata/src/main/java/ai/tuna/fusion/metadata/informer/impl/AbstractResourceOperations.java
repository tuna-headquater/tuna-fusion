package ai.tuna.fusion.metadata.informer.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author robinqu
 */
public abstract class AbstractResourceOperations {

//    static final long RESYNC_INTERVAL = 1000 * 60 * 5;

//    @Getter(value = AccessLevel.PROTECTED)
//    private final SharedInformerFactory sharedInformerFactory;

    private final List<SharedIndexInformer<?>> informers;

    @Getter
    private final KubernetesClient kubernetesClient;

    public AbstractResourceOperations(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.informers = new ArrayList<>();
    }

    public synchronized Future<Void> start() {
        List<CompletableFuture<Void>> startInformerTasks = new ArrayList<>();
        for (SharedIndexInformer<?> informer : informers) {
            CompletableFuture<Void> future = informer.start().toCompletableFuture();
            startInformerTasks.add(future);
        }
        return CompletableFuture.allOf(startInformerTasks.toArray(new CompletableFuture[] {}));
    }

    public synchronized void stop() {
        informers.forEach(SharedIndexInformer::stop);
    }

    public boolean isRunning() {
        return informers.stream().allMatch(SharedIndexInformer::isRunning);
    }

    protected <T extends HasMetadata> SharedIndexInformer<T> createInformer(Class<T> clazz, String... labels) {
        var labelMap = Stream.of(labels)
                .collect(Collectors.toMap(label -> label, label -> "true"));
        var informer = kubernetesClient
                .resources(clazz)
                .withLabels(labelMap)
                .inform();
        informers.add(informer);
        return informer;
    }

}
