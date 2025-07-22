package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.CountedPodAccess;
import ai.tuna.fusion.executor.driver.podpool.FunctionPodAccessException;
import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.executor.web.entity.PagedContent;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
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
                try(var access = functionPodManager.requestAccess(podFunction, podPool)) {
                    list.add(access);
                } catch (Exception e) {
                    sink.error(e);
                    return;
                }
            }
            sink.success(PagedContent.<CountedPodAccess>builder()
                            .items(list)
                    .build());
        });
    }


    @RequestMapping(path = "/namespaces/{namespace}/functions/{functionName}/{*trailingPath}", method = {RequestMethod.DELETE, RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.OPTIONS, RequestMethod.HEAD, RequestMethod.TRACE})
    public Mono<Void> forward(
            @PathVariable String namespace,
            @PathVariable String functionName,
            @PathVariable String trailingPath,
            ServerWebExchange exchange) throws Exception {
        var podFunction = podPoolResources.queryPodFunction(namespace, functionName).orElseThrow();
        var podPool = ResourceUtils.getMatchedOwnerReferenceResourceName(podFunction, PodPool.class)
                .flatMap(name -> podPoolResources.queryPodPool(namespace, name))
                .orElseThrow();
        return HttpProxyUtils.forward(functionPodManager, podFunction, podPool, exchange, trailingPath);
    }

}
