package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.impl.FunctionPodOperationException;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * @author robinqu
 */
@RestController
public class PodFunctionExecutorController {

    private final PodPoolResources podPoolResources;
    private final FunctionPodManager functionPodManager;

    public PodFunctionExecutorController(PodPoolResources podPoolResources, FunctionPodManager functionPodManager) {
        this.podPoolResources = podPoolResources;
        this.functionPodManager = functionPodManager;
    }

    @RequestMapping(path = "/functions/{namespace}/{functionName}/**", method = {RequestMethod.DELETE, RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.OPTIONS, RequestMethod.HEAD, RequestMethod.TRACE})
    public Mono<ResponseEntity<byte[]>> forward(
            @PathVariable String namespace,
            @PathVariable String functionName,
            ProxyExchange<byte[]> proxy) throws FunctionPodOperationException
    {

        var podFunction = podPoolResources.queryPodFunction(namespace, functionName).orElseThrow();
        var podPool = ResourceUtils.getMatchedOwnerReferenceResourceName(podFunction, PodPool.class)
                .flatMap(name -> podPoolResources.queryPodPool(namespace, name))
                .orElseThrow();
        var headlessSvc = podPoolResources.queryPodPoolService(namespace, podPool.getMetadata().getName()).orElseThrow();

        String requestUri = proxy.path();
        String matchedPathPrefix = "/functions/" + namespace + "/" + functionName;
        String trailingPath = requestUri.substring(requestUri.indexOf(matchedPathPrefix) + matchedPathPrefix.length());
        return HttpProxyUtils.forward(functionPodManager, podFunction, podPool, headlessSvc, proxy, trailingPath);
    }

}
