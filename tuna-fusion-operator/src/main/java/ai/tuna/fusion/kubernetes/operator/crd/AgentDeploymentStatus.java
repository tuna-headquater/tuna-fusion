package ai.tuna.fusion.kubernetes.operator.crd;

import lombok.AllArgsConstructor;
import lombok.Builder;
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
