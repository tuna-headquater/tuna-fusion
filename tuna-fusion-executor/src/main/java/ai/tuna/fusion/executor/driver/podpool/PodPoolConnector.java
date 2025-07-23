package ai.tuna.fusion.executor.driver.podpool;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;

/**
 * @author robinqu
 */
public interface PodPoolConnector {

    PodAccess requestAccess(PodFunction podFunction) throws FunctionPodAccessException;

    void disposeAccess(PodAccess podAccess) throws FunctionPodDisposalException;

}
