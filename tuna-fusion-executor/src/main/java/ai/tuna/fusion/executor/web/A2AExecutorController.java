package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.informer.AgentResources;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author robinqu
 */
@RestController
@Slf4j
public class A2AExecutorController {

    private final FunctionPodManager functionPodManager;

    private final PodPoolResources podPoolResources;
    private final AgentResources agentResources;

    public A2AExecutorController(
            FunctionPodManager functionPodManager,
            PodPoolResources podPoolResources,
            AgentResources agentResources
            ) {
        this.functionPodManager = functionPodManager;
        this.podPoolResources = podPoolResources;
        this.agentResources = agentResources;
    }

    @RequestMapping("/a2a/namespaces/{namespace}/agents/{agentDeploymentName}/{*trailingPath}")
    public Mono<Void> forward(
            @PathVariable String namespace,
            @PathVariable String agentDeploymentName,
            @PathVariable String trailingPath,
            ServerWebExchange exchange) throws Exception {
        var agentDeployment = agentResources.queryAgentDeployment(namespace, agentDeploymentName).orElseThrow();
        var agentEnvironmentName = agentDeployment.getSpec().getEnvironmentName();
        var agentEnvironment = agentResources.queryAgentEnvironment(namespace, agentEnvironmentName).orElseThrow();
        Preconditions.checkState(agentEnvironment.getSpec().getDriver().getType() == AgentEnvironmentSpec.DriverType.PodPool, "PodPool driver is required for agent deployment");
        Preconditions.checkState(Strings.CS.equals(agentEnvironmentName, agentDeployment.getSpec().getEnvironmentName()), "mismatch env name in AgentDeployment: expected=%s, actual=%s", agentDeployment.getSpec().getEnvironmentName(), agentEnvironmentName);
        var podPool = podPoolResources.queryPodPool(namespace, agentEnvironment.getStatus().getPodPool().getName()).orElseThrow();
        var podFunction = podPoolResources.queryPodFunction(namespace, agentDeployment.getStatus().getFunction().getFunctionName()).orElseThrow();

        return HttpProxyUtils.forward(
                functionPodManager,
                podFunction,
                podPool,
                exchange,
                trailingPath
        );


    }

}
