package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.informer.AgentResources;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.List;
import java.util.Optional;

/**
 * @author robinqu
 */
public class DefaultAgentResources extends AbstractResourceOperations implements AgentResources {

    private SharedIndexInformer<AgentDeployment> agentDeploymentSharedIndexInformer;
    private SharedIndexInformer<AgentEnvironment> agentEnvironmentSharedIndexInformer;

    public DefaultAgentResources(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected List<SharedIndexInformer<?>> createInformers() {
        this.agentDeploymentSharedIndexInformer = createInformer(AgentDeployment.class);
        this.agentEnvironmentSharedIndexInformer = createInformer(AgentEnvironment.class);
        return List.of(agentDeploymentSharedIndexInformer, agentEnvironmentSharedIndexInformer);
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
