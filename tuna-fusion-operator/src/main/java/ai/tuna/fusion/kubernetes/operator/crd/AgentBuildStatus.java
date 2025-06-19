package ai.tuna.fusion.kubernetes.operator.crd;

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
    private Phase phase;
}
