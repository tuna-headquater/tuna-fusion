package ai.tuna.fusion.executor.driver.podpool;

import ai.tuna.fusion.metadata.crd.podpool.PodPool;

/**
 * @author robinqu
 */
public interface PodPoolConnectorFactory {

    default PodPoolConnector get(PodPool podPool) {
        return get(podPool.getMetadata().getNamespace(), podPool.getMetadata().getName());
    }

    PodPoolConnector get(String ns, String podPoolName);

}
