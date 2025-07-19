package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.MetadataIntegrationTest;
import ai.tuna.fusion.intgrationtest.ResourceTreeNode;
import ai.tuna.fusion.intgrationtest.TestResourceLoader;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author robinqu
 */
public class PodPoolResourcesIT extends MetadataIntegrationTest {

    @Autowired
    private TestResourceLoader resourceLoader;

    @Autowired
    private PodPoolResources podPoolResources;

    private static final ResourceTreeNode RESOURCE_GROUP_1 = ResourceTreeNode.parseYaml("""
            type: PodFunctionBuild
            classpath: yaml/podpool/pod_function_build_1.yaml
            dependencies:
            - type: PodFunction
              classpath: yaml/podpool/pod_function_1.yaml
              dependencies:
              - type: PodPool
                classpath: yaml/podpool/podpool_1.yaml
            """);



    @Test
    public void testResourceQuery() {
        resourceLoader.awaitResourceGroup(RESOURCE_GROUP_1);
        var podPool = podPoolResources.queryPodPool(getTestNamespace(), "test-pool-1");
        assertThat(podPool).isPresent();
    }

}
