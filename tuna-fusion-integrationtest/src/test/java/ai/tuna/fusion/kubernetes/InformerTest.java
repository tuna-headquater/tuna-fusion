package ai.tuna.fusion.kubernetes;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * @author robinqu
 */
@Slf4j
public class InformerTest {

    @Test
    public void testInformer() throws Exception {
        try(KubernetesClient client = new KubernetesClientBuilder().build()) {
            var informers = client.informers();
            var podInformer = informers.sharedIndexInformerFor(Pod.class, 30*1000);
            informers.startAllRegisteredInformers().get();
            log.info("synced {}", podInformer.hasSynced());
            for(var pod : podInformer.getStore().list()) {
                log.info("Pod in store: {}", ResourceUtils.computeResourceMetaKey(pod));
            }
            informers.stopAllRegisteredInformers();
        }
    }

}
