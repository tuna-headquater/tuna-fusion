package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.impl.FunctionPodDisposalException;
import ai.tuna.fusion.executor.driver.podpool.impl.FunctionPodOperationException;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.Service;
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
            Service headlessSvc,
            ProxyExchange<byte[]> proxy,
            String urlPath
    ) throws FunctionPodOperationException {
        var pod = functionPodManager.specializePod(podFunction, podPool);
        var uri = ResourceUtils.getPodUri(pod, headlessSvc, urlPath);
        log.debug("Forward request to Pod {}: {}", pod.getMetadata().getName(), uri);
        return proxy.uri(urlPath).forward().doFinally(signalType -> {
            try {
                functionPodManager.disposePod(pod);
            } catch (FunctionPodDisposalException e) {
                log.warn("Failed to dispose pod", e);
            }
        });
    }

}
