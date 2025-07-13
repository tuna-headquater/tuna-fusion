package ai.tuna.fusion.metadata.informer;

import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.Optional;

/**
 * @author robinqu
 */
public interface AgentResources {
    SharedIndexInformer<AgentDeployment> agentDeployment();
    SharedIndexInformer<AgentEnvironment> agentEnvironment();
    Optional<AgentDeployment> queryAgentDeployment(String namespace, String agentDeploymentName);
    Optional<AgentEnvironment> queryAgentEnvironment(String namespace, String agentEnvironmentName);
}
