package ai.tuna.fusion.metadata.crd;

import ai.tuna.fusion.metadata.a2a.AgentCard;
import io.fabric8.generator.annotation.Required;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentDeploymentSpec {

    @Required
    private String environmentName;

    @Required
    private AgentCard agentCard;

    @Data
    public static class GitOptions {
        String watchedBranchName = "refs/heads/master";
    }

    @Required
    private GitOptions git;
}
