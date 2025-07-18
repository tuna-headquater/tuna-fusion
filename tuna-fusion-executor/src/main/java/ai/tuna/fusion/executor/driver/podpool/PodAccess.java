package ai.tuna.fusion.executor.driver.podpool;

import io.fabric8.kubernetes.api.model.Pod;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author robinqu
 */
@Getter
@SuperBuilder(toBuilder = true)
public class PodAccess {
    private String uri;
    private String namespace;
    private Pod selcetedPod;
    private String podPoolName;
    private String functionName;
    private String functionBuildName;
    private String functionBuildUid;
}
