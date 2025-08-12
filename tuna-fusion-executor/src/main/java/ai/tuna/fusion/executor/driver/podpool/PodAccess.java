package ai.tuna.fusion.executor.driver.podpool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author robinqu
 */
@Getter
@SuperBuilder(toBuilder = true)
@ToString(exclude = "selectedPod")
public class PodAccess {
    private String uri;
    private String namespace;
    private Pod selectedPod;
    private String podPoolName;
    private String functionName;
    private String functionBuildName;
    private String functionBuildUid;
    private Long podTtlInSeconds;
}
