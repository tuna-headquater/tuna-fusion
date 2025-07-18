package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.executor.driver.podpool.PodPoolConnector;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;

/**
 * @author robinqu
 */
public class DefaultPodPoolConnectorFactory extends AbstractPodPoolConnectorFactory {
    public DefaultPodPoolConnectorFactory(PodPoolResources podPoolResources) {
        super(podPoolResources);
    }

    @Override
    protected PodPoolConnector createPodQueue(PodPool podPool) {
        return new ApiServerPodPoolConnectorImpl(getPodPoolResources(), podPool);
    }
}
