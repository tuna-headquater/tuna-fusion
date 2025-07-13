package ai.tuna.fusion.metadata.informer.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import lombok.Getter;

/**
 * @author robinqu
 */
public abstract class AbstractResourceOperations {

    static final long RESYNC_INTERVAL = 1000 * 60 * 5;

    @Getter
    private final SharedInformerFactory sharedInformerFactory;

    public AbstractResourceOperations(KubernetesClient kubernetesClient) {
        sharedInformerFactory = kubernetesClient.informers();
    }

    public void start() {
        sharedInformerFactory.startAllRegisteredInformers();
    }

    public void stop() {
        sharedInformerFactory.stopAllRegisteredInformers();
    }

    public abstract boolean isRunning();

    protected <T extends HasMetadata> SharedIndexInformer<T> createInformer(Class<T> clazz) {
        return sharedInformerFactory.sharedIndexInformerFor(clazz, RESYNC_INTERVAL);
    }

}
