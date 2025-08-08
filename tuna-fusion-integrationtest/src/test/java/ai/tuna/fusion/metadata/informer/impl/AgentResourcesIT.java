package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.IntegrationTest;
import ai.tuna.fusion.TestResourceGroups;
import ai.tuna.fusion.intgrationtest.TestResourceContext;
import ai.tuna.fusion.metadata.crd.AgentResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionSpec;
import ai.tuna.fusion.metadata.informer.AgentResources;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author robinqu
 */
public class AgentResourcesIT extends IntegrationTest {

    @Autowired
    private AgentResources agentResources;

    @Autowired
    private PodPoolResources podPoolResources;

    @Test
    void testResourceBootstrap(TestResourceContext context) {
        context.awaitResourceGroup(TestResourceGroups.RESOURCE_GROUP_2);
        var deploy1 = agentResources.queryAgentDeployment(context.targetNamespace(), "test-deploy-1");
        assertThat(deploy1).isPresent();
        var env1 = agentResources.queryAgentEnvironment(context.targetNamespace(), "agent-env-1");
        assertThat(env1).isPresent();
        var pf1 = podPoolResources.queryPodFunction(context.targetNamespace(), AgentResourceUtils.computeFunctionName(deploy1.get()));
        assertThat(pf1).isPresent();
        pf1.ifPresent(podFunction -> {
            assertThat(podFunction.getSpec().getConfigmaps()).hasSize(1);
            assertThat(podFunction.getSpec().getSecrets()).hasSize(1);
        });
    }

}
