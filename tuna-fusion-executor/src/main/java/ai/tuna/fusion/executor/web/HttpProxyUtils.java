package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * @author robinqu
 */
@Slf4j
public class HttpProxyUtils {

    public static Mono<ResponseEntity<byte[]>> forward(
            FunctionPodManager functionPodManager,
            PodFunction podFunction,
            PodPool podPool,
            ProxyExchange<byte[]> proxy,
            String trailingPath
    ) throws Exception {
        try (var access = functionPodManager.requestAccess(podFunction, podPool, trailingPath)) {
            log.debug("[forward] PodAccess required: {}", access);
            return proxy.uri(access.getUri()).forward();
        }

    }

}
