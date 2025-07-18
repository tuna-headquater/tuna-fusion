package ai.tuna.fusion.executor.driver.podpool;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import lombok.Getter;

/**
 * @author robinqu
 */
public class FunctionSpecilizationException extends Exception {
    @Getter
    private final PodPool podPool;
    @Getter
    private final PodFunction podFunction;

    public static FunctionSpecilizationException of(PodPool podPool, PodFunction podFunction, String message) {
        return new FunctionSpecilizationException(message, podPool, podFunction);
    }

    public FunctionSpecilizationException(PodPool podPool, PodFunction podFunction) {
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionSpecilizationException(String message, PodPool podPool, PodFunction podFunction) {
        super(message);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionSpecilizationException(String message, Throwable cause, PodPool podPool, PodFunction podFunction) {
        super(message, cause);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }

    public FunctionSpecilizationException(Throwable cause, PodPool podPool, PodFunction podFunction) {
        super(cause);
        this.podPool = podPool;
        this.podFunction = podFunction;
    }
}
