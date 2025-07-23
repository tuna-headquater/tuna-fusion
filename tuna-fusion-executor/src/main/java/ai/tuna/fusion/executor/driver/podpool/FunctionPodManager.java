package ai.tuna.fusion.executor.driver.podpool;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;

import java.util.List;

/**
 * @author robinqu
 */
public interface FunctionPodManager {

    CountedPodAccess requestAccess(PodFunction function, PodPool podPool) throws FunctionPodAccessException;
    
    void disposeAccess(CountedPodAccess countedPodAccess) throws FunctionPodDisposalException;

    List<CountedPodAccess> listAccess(PodFunction function, PodPool podPool);
}
