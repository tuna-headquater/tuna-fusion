package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.informer.AgentResources;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.Optional;

/**
 * @author robinqu
 */
public class DefaultAgentResources extends AbstractResourceOperations implements AgentResources {

    private final SharedIndexInformer<AgentDeployment> agentDeploymentSharedIndexInformer = createInformer(AgentDeployment.class);
    private final SharedIndexInformer<AgentEnvironment> agentEnvironmentSharedIndexInformer = createInformer(AgentEnvironment.class);

    public DefaultAgentResources(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    public SharedIndexInformer<AgentDeployment> agentDeployment() {
        return agentDeploymentSharedIndexInformer;
    }

    @Override
    public SharedIndexInformer<AgentEnvironment> agentEnvironment() {
        return agentEnvironmentSharedIndexInformer;
    }

    @Override
    public Optional<AgentDeployment> queryAgentDeployment(String namespace, String agentDeploymentName) {
        return ResourceUtils.getResourceFromInformer(agentDeploymentSharedIndexInformer, namespace, agentDeploymentName);
    }

    @Override
    public Optional<AgentEnvironment> queryAgentEnvironment(String namespace, String agentEnvironmentName) {
        return ResourceUtils.getResourceFromInformer(agentEnvironmentSharedIndexInformer, namespace, agentEnvironmentName);
    }
}
