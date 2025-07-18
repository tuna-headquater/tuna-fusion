package ai.tuna.fusion.executor.driver.podpool;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;

/**
 * @author robinqu
 */
public interface FunctionPodManager {

    CountedPodAccess requestAccess(PodFunction function, PodPool podPool, String trailingPath) throws FunctionSpecilizationException;
    
    void disposeAccess(CountedPodAccess countedPodAccess) throws FunctionPodDisposalException;
}
