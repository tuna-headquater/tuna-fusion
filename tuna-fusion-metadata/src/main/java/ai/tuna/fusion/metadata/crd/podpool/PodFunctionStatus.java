package ai.tuna.fusion.metadata.crd.podpool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodFunctionStatus {
    @Data
    public static class BuildInfo {
        private String name;
        private String uid;
        private long startTimestamp;
        private PodFunctionBuildStatus.Phase phase;
    }
    private BuildInfo currentBuild;
    private BuildInfo effectiveBuild;
}
