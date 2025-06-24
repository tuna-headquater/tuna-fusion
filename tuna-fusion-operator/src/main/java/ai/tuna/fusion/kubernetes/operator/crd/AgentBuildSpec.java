package ai.tuna.fusion.kubernetes.operator.crd;

import lombok.*;

/**
 * @author robinqu
 */
@Data
public class AgentBuildSpec {
    private String gitCommitId;
    private String buildScript;
    private String builderImage;
    private String serviceAccountName;
}
