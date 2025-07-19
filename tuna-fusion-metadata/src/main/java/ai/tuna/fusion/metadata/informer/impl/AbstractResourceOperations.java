package ai.tuna.fusion.metadata.informer.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public abstract class AbstractResourceOperations {
    private final List<SharedIndexInformer<?>> informers;

    @Getter
    private final KubernetesClient kubernetesClient;

    public AbstractResourceOperations(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.informers = new ArrayList<>();
    }

    public synchronized Future<Void> start() {
        informers.addAll(createInformers());
        log.info("Start {} with {} informers", getClass().getName(), informers.size());
        List<CompletableFuture<Void>> startInformerTasks = new ArrayList<>();
        for (SharedIndexInformer<?> informer : informers) {
            CompletableFuture<Void> future = informer.start().toCompletableFuture();
            startInformerTasks.add(future);
        }
        return CompletableFuture.allOf(startInformerTasks.toArray(new CompletableFuture[] {}));
    }

    public synchronized void stop() {
        log.info("Stop {} with {} informers", getClass().getName(), informers.size());
        informers.forEach(SharedIndexInformer::stop);
        informers.clear();
    }

    public boolean isRunning() {
        return informers.stream().allMatch(SharedIndexInformer::isRunning);
    }

    protected abstract List<SharedIndexInformer<?>> createInformers();

    protected <T extends HasMetadata> SharedIndexInformer<T> createInformer(Class<T> clazz, String... labels) {
        var labelMap = Stream.of(labels)
                .collect(Collectors.toMap(label -> label, label -> "true"));
        log.info("Create informer for {} with labels {}", clazz.getSimpleName(), labelMap);
        return kubernetesClient
                .resources(clazz)
                .withLabels(labelMap)
                .inform();
    }

}
