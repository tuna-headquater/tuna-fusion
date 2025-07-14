package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import lombok.Getter;

/**
 * @author robinqu
 */
public class FunctionPodOperationException extends Exception {
    @Getter
    private final PodPool podPool;
    @Getter
    private final PodFunction podFunction;

    public static FunctionPodOperationException of(PodPool podPool, PodFunction podFunction, String message) {
        return new FunctionPodOperationException(message, podPool, podFunction);
    }

    public FunctionPodOperationException(PodPool podPool, PodFunction podFunction) {
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionPodOperationException(String message, PodPool podPool, PodFunction podFunction) {
        super(message);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionPodOperationException(String message, Throwable cause, PodPool podPool, PodFunction podFunction) {
        super(message, cause);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionPodOperationException(Throwable cause, PodPool podPool, PodFunction podFunction) {
        super(cause);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }
}
