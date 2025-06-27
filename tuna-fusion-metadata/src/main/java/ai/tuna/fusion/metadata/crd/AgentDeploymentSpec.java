package ai.tuna.fusion.metadata.crd;

import io.github.vishalmysore.a2a.domain.AgentCard;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentDeploymentSpec {

    private String environmentName;
    private AgentCard agentCard;

    @Data
    public static class GitOptions {
        String watchedBranchName = "refs/heads/master";
    }
    private GitOptions git;

    @Data
    public static class BuildRecipe {
        private String buildScript;
        private String builderImage;
        private String serviceAccountName;
    }
    private BuildRecipe buildRecipe;

}
