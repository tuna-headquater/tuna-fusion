package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.generator.annotation.Required;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class PodFunctionStatus {
    @Data
    public static class BuildInfo {
        private String name;
        private long startTimestamp;
    }
    private BuildInfo currentBuild;
    private BuildInfo effectiveBuild;
}
