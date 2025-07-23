package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author robinqu
 */
@Slf4j
public class HttpProxyUtils {

    private static final WebClient webClient = WebClient.create();

    public static Mono<Void> forward(
            FunctionPodManager functionPodManager,
            PodFunction podFunction,
            PodPool podPool,
            ServerWebExchange exchange,
            String trailingPath
    ) throws Exception {
        // this access is closed asynchronously, so we need to close it after we finish
        var access = functionPodManager.requestAccess(podFunction, podPool);
        var fullUrl = ResourceUtils.getPodUri(access.getPodAccess().getSelectedPod(), trailingPath);
        log.debug("[forward] {} {}", exchange.getRequest().getMethod(), fullUrl);
        var requestBody = exchange.getRequest().getBody();
        var response = exchange.getResponse();
        var headers = exchange.getRequest().getHeaders();
        HttpHeaders forwardHeaders = new HttpHeaders();
        headers.entrySet().stream()
                .filter(entry -> !entry.getKey().equalsIgnoreCase("host"))
                .filter(entry -> !entry.getKey().equalsIgnoreCase("content-length"))
                .forEach(entry -> forwardHeaders.put(entry.getKey(), entry.getValue()));
        return webClient.post()
                .uri(fullUrl)
                .headers(h -> h.addAll(forwardHeaders))
                .body(requestBody, DataBuffer.class)
                .exchangeToFlux(clientResponse -> {
                    response.setStatusCode(clientResponse.statusCode());
                    response.getHeaders().putAll(clientResponse.headers().asHttpHeaders());
                    return clientResponse.bodyToFlux(DataBuffer.class);
                })
                .transform(response::writeWith)
                .then().doFinally(signalType -> {
                    try {
                        access.close();
                    } catch (Exception e) {
                        log.warn("[forward] Ignored exception during closing CountedPodAccess: {}", access, e);
                    }
                });

    }

}
