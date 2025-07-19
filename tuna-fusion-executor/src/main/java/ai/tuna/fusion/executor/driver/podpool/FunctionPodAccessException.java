package ai.tuna.fusion.executor.driver.podpool;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import lombok.Getter;

/**
 * @author robinqu
 */
public class FunctionPodAccessException extends Exception {
    @Getter
    private final PodPool podPool;
    @Getter
    private final PodFunction podFunction;

    public static FunctionPodAccessException of(PodPool podPool, PodFunction podFunction, String message) {
        return new FunctionPodAccessException(message, podPool, podFunction);
    }

    public FunctionPodAccessException(PodPool podPool, PodFunction podFunction) {
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionPodAccessException(String message, PodPool podPool, PodFunction podFunction) {
        super(message);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionPodAccessException(String message, Throwable cause, PodPool podPool, PodFunction podFunction) {
        super(message, cause);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionPodAccessException(Throwable cause, PodPool podPool, PodFunction podFunction) {
        super(cause);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }
}
