package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.IntegrationTest;
import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.intgrationtest.TestResourceContext;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashSet;
import java.util.Set;

import static ai.tuna.fusion.TestResourceGroups.RESOURCE_GROUP_1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author robinqu
 */
@Slf4j
public class DefaultFunctionPodManagerIT extends IntegrationTest {

    @Autowired
    private FunctionPodManager functionPodManager;

    @Autowired
    private PodPoolResources podPoolResources;

    @Autowired
    private KubernetesClient kubernetesClient;

    private final WebClient webClient = WebClient.create();

    @Test
    public void testRequestAccess(TestResourceContext context) throws Exception {
        context.awaitResourceGroup(RESOURCE_GROUP_1);
        var podPool = podPoolResources.queryPodPool(context.getTargetNamespace(), "test-pool-1").orElseThrow();

        var fn1 = podPoolResources.queryPodFunction(context.getTargetNamespace(), "test-function-1").orElseThrow();

        var maxCount = podPool.getSpec().getRunPerPod();
        var count = 0;
        Pod selectedPod = null;
        Set<String> podNamesSet = new HashSet<>();
        while (count++<maxCount) {
            try(var access = functionPodManager.requestAccess(fn1, podPool)) {
                selectedPod = access.getPodAccess().getSelectedPod();
                log.info("Selected pod {}", ResourceUtils.computeResourceMetaKey(selectedPod));
                assertThat(selectedPod).isNotNull();
                assertThat(access.getUsageCount().intValue()).isEqualTo(count);
                assertThat(access.getMaxUsageCount()).isEqualTo(maxCount);
                boolean first = podNamesSet.isEmpty();
                assertThat(podNamesSet.add(selectedPod.getMetadata().getName()))
                        .isEqualTo(first);
                var body = webClient.get().uri(access.getPodAccess().getUri()).retrieve();
                log.info("body={}", body.bodyToMono(String.class).block());
            }
        }
        assertThat(selectedPod).isNotNull();
        var checkedPod = ResourceUtils.getPod(kubernetesClient,
                selectedPod.getMetadata().getNamespace(),
                selectedPod.getMetadata().getName());
        var deleted = checkedPod
                .map(pod -> StringUtils.isNoneBlank(pod.getMetadata().getDeletionTimestamp()))
                .orElse(true);
        assertThat(deleted).isTrue();

        // another access would trigger rotation of selected pod
        try(var access = functionPodManager.requestAccess(fn1, podPool)) {
            selectedPod = access.getPodAccess().getSelectedPod();
            log.info("Selected pod {}", ResourceUtils.computeResourceMetaKey(selectedPod));
            assertThat(selectedPod).isNotNull();
            assertThat(podNamesSet.add(selectedPod.getMetadata().getName()))
                    .isTrue();
        }
    }


}
