package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.executor.driver.podpool.PodPoolConnector;
import ai.tuna.fusion.executor.driver.podpool.PodPoolConnectorFactory;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author robinqu
 */
@Slf4j
public abstract class AbstractPodPoolConnectorFactory implements PodPoolConnectorFactory, ResourceEventHandler<PodPool> {

    @Getter(value = AccessLevel.PROTECTED)
    private final PodPoolResources podPoolResources;

    private final Map<String, PodPoolConnector> podPoolConnectors;

    public AbstractPodPoolConnectorFactory(PodPoolResources podPoolResources) {
        this.podPoolResources = podPoolResources;
        this.podPoolConnectors = new ConcurrentHashMap<>(128);
        this.podPoolResources.podPool().addEventHandler(this);
    }

    protected abstract PodPoolConnector createPodQueue(PodPool podPool);

    @Override
    public PodPoolConnector get(String ns, String podPoolName) {
        return podPoolConnectors.computeIfAbsent("%s/%s".formatted(ns, podPoolName), key -> {
            var podPool = podPoolResources.queryPodPool(ns, podPoolName).orElseThrow();
            return createPodQueue(podPool);
        });
    }

    @Override
    public void onAdd(PodPool obj) {
        var ret = podPoolConnectors.put(
                ResourceUtils.computeResourceMetaKey(obj),
                createPodQueue(obj)
        );
        if (Objects.isNull(ret)) {
            log.info("[onAdd] PodPoolConnector created for PodPool {}", ResourceUtils.computeResourceMetaKey(obj));
        } else {
            log.warn("[onAdd] Possible conflicted PodPool:  {}", ResourceUtils.computeResourceMetaKey(obj));
        }
    }

    @Override
    public void onUpdate(PodPool oldObj, PodPool newObj) {
        log.debug("[onUpdate] Do nothing in onUpdate");
    }

    @Override
    public void onDelete(PodPool obj, boolean deletedFinalStateUnknown) {
        var ret = podPoolConnectors.remove(ResourceUtils.computeResourceMetaKey(obj));
        if (Objects.nonNull(ret)) {
            log.info("[onDelete] PodPoolConnector removed for PodPool {}", ResourceUtils.computeResourceMetaKey(obj));
        } else {
            log.warn("[onDelete] PodPoolConnector not found for PodPool {}", ResourceUtils.computeResourceMetaKey(obj));
        }
    }
}
