package ai.tuna.fusion.intgrationtest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author robinqu
 */
@Slf4j
public class ResourceLoaderTest {

    @Test
    void testParseYAML() {
        ResourceTreeNode node = ResourceTreeNode.parseYaml("""
            type: PodFunctionBuild
            classpath: yaml/podpool/pod_function_build_1.yaml
            dependencies:
            - type: PodFunction
              classpath: yaml/podpool/pod_function_1.yaml
              dependencies:
              - type: PodPool
                classpath: yaml/podpool/podpool_1.yaml
            """);
        log.info("Parsed YAML: {}", node);
        assertThat(node).isNotNull();
        assertThat(node.getClasspath()).isNotBlank();
        assertThat(node.getType()).isNotBlank();
        assertThat(node.getDependencies()).isNotEmpty();
    }


}
