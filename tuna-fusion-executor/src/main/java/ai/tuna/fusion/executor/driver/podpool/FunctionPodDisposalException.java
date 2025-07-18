package ai.tuna.fusion.executor.driver.podpool;

import io.fabric8.kubernetes.api.model.Pod;
import lombok.Getter;

/**
 * @author robinqu
 */
public class FunctionPodDisposalException extends Exception {
    @Getter
    private final Pod pod;

    public static FunctionPodDisposalException of(Pod pod, String message) {
        return new FunctionPodDisposalException(message, pod);
    }

    public FunctionPodDisposalException(Pod pod) {
        this.pod = pod;
    }

    public FunctionPodDisposalException(String message, Pod pod) {
        super(message);
        this.pod = pod;
    }

    public FunctionPodDisposalException(String message, Throwable cause, Pod pod) {
        super(message, cause);
        this.pod = pod;
    }

    public FunctionPodDisposalException(Throwable cause, Pod pod) {
        super(cause);
        this.pod = pod;
    }
}
