package ai.tuna.fusion.kubernetes.operator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author robinqu
 */
@ConfigurationProperties(prefix = "operator")
@Data
public class OperatorProperties {

    @Data
    public static class LeaderElection {
        private boolean enabled = false;
        private String namespace = "default";
        private String identity = String.valueOf(ProcessHandle.current().pid());
    }

    private LeaderElection leaderElection;
    private String sharedArchivePvcName;
    private String builderServiceAccountName;
    private String runtimeServiceAccountName;

}
