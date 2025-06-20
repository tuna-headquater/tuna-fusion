package ai.tuna.fusion.kubernetes.operator.crd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentDeploymentSpec {

    private String agentName;
    private int agentCatalogueId;
    private int agentRepositoryId;
    private String agentCatalogueName;
    private String gitRepositoryUrl;
    private String agentEnvironmentName;

}
