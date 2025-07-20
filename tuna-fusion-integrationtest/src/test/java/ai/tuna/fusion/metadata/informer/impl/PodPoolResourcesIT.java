package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.MetadataIntegrationTest;
import ai.tuna.fusion.intgrationtest.TestResourceContext;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static ai.tuna.fusion.TestResourceGroups.RESOURCE_GROUP_1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author robinqu
 */
public class PodPoolResourcesIT extends MetadataIntegrationTest {

    @Autowired
    private PodPoolResources podPoolResources;

    @Test
    public void testResourceQuery(TestResourceContext context) {
        context.awaitResourceGroup(RESOURCE_GROUP_1);
        var podPool = podPoolResources.queryPodPool(context.getTargetNamespace(), "test-pool-1");
        assertThat(podPool).isPresent();
        var fn1 = podPoolResources.queryPodFunction(context.getTargetNamespace(), "test-function-1");
        assertThat(fn1).isPresent();
        var build1 = podPoolResources.queryPodFunctionBuild(context.getTargetNamespace(), "test-pod-function-build-1");
        assertThat(build1).isPresent();
    }

}
