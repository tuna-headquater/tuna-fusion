package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.informer.AgentResources;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * @author robinqu
 */
@Slf4j
public class DefaultAgentResources extends AbstractResourceOperations implements AgentResources {

    public DefaultAgentResources(KubernetesClient kubernetesClient, InformerProperties informerProperties) {
        super(kubernetesClient, informerProperties);
    }

    @Override
    public Optional<SharedIndexInformer<AgentDeployment>> agentDeployment(String namespace) {
        return getSharedInformer(AgentDeployment.class, namespace);
    }

    @Override
    public Optional<SharedIndexInformer<AgentEnvironment>> agentEnvironment(String namespace) {
        return getSharedInformer(AgentEnvironment.class, namespace);
    }

    @Override
    public Optional<AgentDeployment> queryAgentDeployment(String namespace, String agentDeploymentName) {
        log.debug("queryAgentDeployment: {}/{}", namespace, agentDeploymentName);
        return ResourceUtils.getResourceFromInformer(
                agentDeployment(namespace).orElseThrow(),
                namespace,
                agentDeploymentName
        );
    }

    @Override
    public Optional<AgentEnvironment> queryAgentEnvironment(String namespace, String agentEnvironmentName) {
        log.debug("queryAgentEnvironment: {}/{}", namespace, agentEnvironmentName);
        return ResourceUtils.getResourceFromInformer(
                agentEnvironment(namespace).orElseThrow(),
                namespace,
                agentEnvironmentName
        );
    }

    @Override
    protected void configureInformers() {
        prepareInformers(AgentDeployment.class);
        prepareInformers(AgentEnvironment.class);
    }
}
