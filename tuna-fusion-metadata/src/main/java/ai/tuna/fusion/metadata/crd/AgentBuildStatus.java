package ai.tuna.fusion.metadata.crd;

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
        private String podPhase;
        private String logs;
    }

    private Phase phase;
    private JobPodInfo jobPod;

}
