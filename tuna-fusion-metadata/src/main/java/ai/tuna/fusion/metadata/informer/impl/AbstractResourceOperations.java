package ai.tuna.fusion.metadata.informer.impl;

import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Informable;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author robinqu
 */
@Slf4j
public abstract class AbstractResourceOperations {

    private final List<ResourceInformersWrapper<? extends HasMetadata>> informersWrapper;

    @Getter
    private final KubernetesClient kubernetesClient;

    private final InformerProperties informerProperties;

    public AbstractResourceOperations(KubernetesClient kubernetesClient, InformerProperties informerProperties) {
        this.informerProperties = informerProperties;
        this.kubernetesClient = kubernetesClient;
        this.informersWrapper = new ArrayList<>();
        Preconditions.checkNotNull(informerProperties);
        Preconditions.checkNotNull(informerProperties.getClusterScoped(), "should assign informer.clusterScoped");
        if (informerProperties.getClusterScoped()) {
            Preconditions.checkState(informerProperties.getNamespaces() == null || informerProperties.getNamespaces().isEmpty(), "should leave informer.namespaces empty");
        } else {
            Preconditions.checkNotNull(informerProperties.getNamespaces(), "should assign informer.namespaces");
            Preconditions.checkArgument(!informerProperties.getNamespaces().isEmpty(), "should assign informer.namespaces");
        }
    }

    @SneakyThrows
    public synchronized void start() {
        if (!informersWrapper.isEmpty()) {
            throw new IllegalStateException("informers should be empty before started.");
        }
        configureInformers();
        informersWrapper.forEach(ResourceInformersWrapper::start);
    }


    public synchronized void stop() {
        log.info("Stop {} with {} informers", getClass().getName(), informersWrapper.size());
        informersWrapper.forEach(ResourceInformersWrapper::stop);
        informersWrapper.clear();
    }

    protected abstract void configureInformers();

    protected <T extends HasMetadata> void prepareInformers(Class<T> clazz) {
        if (informerProperties.getClusterScoped()) {
            log.info("[createInformer] Create informer for {} in all namespaces", clazz.getSimpleName());
            informersWrapper.add(
                    new ResourceInformersWrapper<>(
                            createInformer(kubernetesClient.resources(clazz)
                                    .inAnyNamespace()),
                        clazz
                    )
            );
        } else {
            var sharedInformers = informerProperties.getNamespaces().stream().collect(Collectors.toMap(ns -> ns, ns -> {
                log.info("[createInformer] Create informer for {} in namespace {}", clazz.getSimpleName(), ns);
                return createInformer(
                        kubernetesClient.resources(clazz).inNamespace(ns)
                );
            }));
            informersWrapper.add(
                    new ResourceInformersWrapper<>(sharedInformers, clazz)
            );
        }
    }

    private  <T extends HasMetadata> SharedIndexInformer<T> createInformer(Informable<T> informable) {
        return Optional.ofNullable(informerProperties.getInformerListLimit())
                .map(informable::withLimit)
                .orElse(informable)
                .runnableInformer(0);
    }

    public <T extends HasMetadata> Optional<SharedIndexInformer<T>> getSharedInformer(Class<T> clazz, String namespace) {
        return getInformersWrapper(clazz)
                .flatMap(wrapper -> wrapper.getSharedIndexInformer(namespace));
    }

    public <T extends HasMetadata> Optional<ResourceInformersWrapper<T>> getInformersWrapper(Class<T> clazz) {
        //noinspection unchecked
        return informersWrapper.stream().filter(wrapper -> wrapper.getResourceType().equals(clazz))
                .findFirst()
                .map(wrapper -> (ResourceInformersWrapper<T>)wrapper);
    }

}
