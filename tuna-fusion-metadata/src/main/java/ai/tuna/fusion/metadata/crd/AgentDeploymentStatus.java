package ai.tuna.fusion.metadata.crd;

import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentDeploymentStatus {
    @Data
    public static class BuildInfo {
        String name;
        long startTimestamp;
    }

    private BuildInfo currentBuild;
}
