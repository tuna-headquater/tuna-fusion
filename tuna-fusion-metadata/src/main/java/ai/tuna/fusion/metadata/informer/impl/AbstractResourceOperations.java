package ai.tuna.fusion.metadata.informer.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author robinqu
 */
@Slf4j
public abstract class AbstractResourceOperations {
    private final List<SharedIndexInformer<?>> informers;
    private final SharedInformerFactory informerFactory;

    @Getter
    private final KubernetesClient kubernetesClient;

    public AbstractResourceOperations(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.informers = new ArrayList<>();
        this.informerFactory = kubernetesClient.informers();
    }

    @SneakyThrows
    public synchronized void start() {
        informers.addAll(createInformers());
        log.info("Start {} with {} informers", getClass().getName(), informers.size());
        informerFactory.startAllRegisteredInformers().get(10, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        log.info("Stop {} with {} informers", getClass().getName(), informers.size());
        informerFactory.stopAllRegisteredInformers();
        informers.clear();
    }

    public boolean isRunning() {
        return informers.stream().allMatch(SharedIndexInformer::isRunning);
    }

    protected abstract List<SharedIndexInformer<?>> createInformers();

    protected <T extends HasMetadata> SharedIndexInformer<T> createInformer(Class<T> clazz) {
//        var labelMap = Stream.of(labels)
//                .collect(Collectors.toMap(label -> label, label -> "true"));
        log.info("Create informer for {} in all namespaces", clazz.getSimpleName());
        return informerFactory.sharedIndexInformerFor(clazz, 30*1000);
    }

}
