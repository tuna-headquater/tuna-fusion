package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.impl.FunctionPodDisposalException;
import ai.tuna.fusion.executor.driver.podpool.impl.FunctionPodOperationException;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.informer.AgentResources;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @RequestMapping("/agent/{namespace}/{agentCatalogueName}/{agentDeploymentName}/**")
    public Mono<ResponseEntity<byte[]>> forward(
            @PathVariable String namespace,
            @PathVariable String agentCatalogueName,
            @PathVariable String agentDeploymentName,
            ProxyExchange<byte[]> proxy) throws FunctionPodOperationException {
        var agentDeployment = agentResources.queryAgentDeployment(namespace, agentDeploymentName).orElseThrow();
        var agentEnvironment = agentResources.queryAgentEnvironment(namespace, agentDeployment.getSpec().getEnvironmentName()).orElseThrow();
        if (agentEnvironment.getSpec().getDriver().getType() != AgentEnvironmentSpec.DriverType.PodPool) {
            throw new IllegalStateException("PodPool driver is required for agent deployment");
        }
        var podPool = podPoolResources.queryPodPool(namespace, agentEnvironment.getStatus().getPodPool().getName()).orElseThrow();
        var headlessSvc = podPoolResources.queryPodPoolService(namespace, podPool.getStatus().getHeadlessServiceName()).orElseThrow();
        var podFunction = podPoolResources.queryPodFunction(namespace, agentDeployment.getStatus().getFunction().getFunctionName()).orElseThrow();

        var pod = functionPodManager.specializePod(podFunction, podPool);
        String requestUri = proxy.path();
        String matchedPathPrefix = "/agents/" + namespace + "/" + agentCatalogueName + "/" + agentDeploymentName;
        String trailingPath = requestUri.substring(requestUri.indexOf(matchedPathPrefix) + matchedPathPrefix.length());
        var uri = getPodUri(pod, headlessSvc, trailingPath);
        log.debug("Forward request to Pod: {}", uri);
        return proxy.uri(trailingPath).forward().doFinally(signalType -> {
            try {
                functionPodManager.disposePod(pod);
            } catch (FunctionPodDisposalException e) {
                log.warn("Failed to dispose pod", e);
            }
        });
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static final String POD_HTTP_URL = "http://%s.%s.%s.svc.cluster.local:%s/%s";
    private String getPodUri(Pod pod, Service service, String subPath) {
        return String.format(POD_HTTP_URL,
                pod.getMetadata().getName(),
                service.getMetadata().getName(),
                pod.getMetadata().getNamespace(),
                pod.getSpec().getContainers().getFirst().getPorts().getFirst().getContainerPort(),
                subPath);
    }



}
