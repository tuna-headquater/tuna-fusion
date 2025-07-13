package ai.tuna.fusion.executor.driver.podpool;

import ai.tuna.fusion.executor.driver.podpool.impl.FunctionPodDisposalException;
import ai.tuna.fusion.executor.driver.podpool.impl.FunctionPodOperationException;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.Pod;

/**
 * @author robinqu
 */
public interface FunctionPodManager {

    Pod specializePod(PodFunction function, PodPool podPool) throws FunctionPodOperationException;

    void disposePod(Pod pod) throws FunctionPodDisposalException;

}
