package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.CountedPodAccess;
import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.FunctionPodAccessException;
import ai.tuna.fusion.executor.web.entity.PagedContent;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

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


    @GetMapping("/namespaces/{namespace}/functions")
    public Mono<PagedContent<CountedPodAccess>> listPodFunctions(@PathVariable String namespace) {
        return Mono.create(sink -> {
            List<CountedPodAccess> list = new ArrayList<>();
            var functions = podPoolResources.podFunction().getStore().list();
            for (var podFunction : functions) {
                var podPool = ResourceUtils.getMatchedOwnerReferenceResourceName(podFunction, PodPool.class)
                        .flatMap(name -> podPoolResources.queryPodPool(namespace, name))
                        .orElseThrow();
                try {
                    list.add(functionPodManager.requestAccess(podFunction, podPool));
                } catch (FunctionPodAccessException e) {
                    sink.error(e);
                    return;
                }
            }
            sink.success(PagedContent.<CountedPodAccess>builder()
                            .items(list)
                    .build());
        });
    }


    @RequestMapping(path = "/namespaces/{namespace}/functions/{functionName}/**", method = {RequestMethod.DELETE, RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.OPTIONS, RequestMethod.HEAD, RequestMethod.TRACE})
    public Mono<ResponseEntity<byte[]>> forward(
            @PathVariable String namespace,
            @PathVariable String functionName,
            ProxyExchange<byte[]> proxy) throws Exception {

        var podFunction = podPoolResources.queryPodFunction(namespace, functionName).orElseThrow();
        var podPool = ResourceUtils.getMatchedOwnerReferenceResourceName(podFunction, PodPool.class)
                .flatMap(name -> podPoolResources.queryPodPool(namespace, name))
                .orElseThrow();

        String requestUri = proxy.path();
        String matchedPathPrefix = "/functions/" + namespace + "/" + functionName;
        String trailingPath = requestUri.substring(requestUri.indexOf(matchedPathPrefix) + matchedPathPrefix.length());
        return HttpProxyUtils.forward(functionPodManager, podFunction, podPool, proxy, trailingPath);
    }

}
