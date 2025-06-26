package ai.tuna.fusion.metadata.crd;

import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentDeploymentSpec {

    private String agentCatalogueName;
    private String agentEnvironmentName;

    @Data
    public static class GitOptions {
        String watchedBranchName = "refs/heads/master";
    }
    private GitOptions git;

}
