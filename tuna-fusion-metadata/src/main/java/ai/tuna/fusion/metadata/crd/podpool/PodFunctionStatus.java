package ai.tuna.fusion.metadata.crd.podpool;

import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class PodFunctionStatus {
    @Data
    public static class BuildInfo {
        private String name;
        private String uid;
        private long startTimestamp;
        private PodFunctionBuildStatus status;
    }
    private BuildInfo currentBuild;
    private BuildInfo effectiveBuild;
}
