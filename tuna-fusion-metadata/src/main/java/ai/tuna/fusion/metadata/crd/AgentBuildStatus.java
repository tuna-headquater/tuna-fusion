package ai.tuna.fusion.metadata.crd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentBuildStatus {
    public enum Phase {
        Pending,
        Scheduled,
        Running,
        Succeeded,
        Failed,
        Terminating
    }

    @Data
    public static class JobPodInfo {
        private String podName;
    }

    private Phase phase;
    private JobPodInfo jobPod;

}
