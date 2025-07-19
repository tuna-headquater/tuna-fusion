package ai.tuna.fusion.intgrationtest;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.*;

import java.util.List;

/**
 * @author robinqu
 */
@Data
@lombok.Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ResourceTreeNode {

    public static ResourceTreeNode parseYaml(String definitions) {
        return ResourceTreeParser.DEFAULT.parse(definitions);
    }

    public static Class<? extends HasMetadata> resolveType(String typeName) {
        return switch (typeName) {
            case "PodPool" -> PodPool.class;
            case "PodFunction" -> PodFunction.class;
            case "PodFunctionBuild" -> PodFunctionBuild.class;
            default -> throw new IllegalArgumentException("Invalid type name: " + typeName);
        };
    }
    public static ResourceTreeNode podPool(String classpath) {
        return ResourceTreeNode.builder()
                .classpath(classpath)
                .type(PodPool.class.getSimpleName())
                .build();
    }

    public static ResourceTreeNode podFunction(String classpath) {
        return ResourceTreeNode.builder()
                .classpath(classpath)
                .type(PodFunction.class.getSimpleName())
                .build();
    }

    public static ResourceTreeNode podFunctionBuild(String classpath) {
        return ResourceTreeNode.builder()
                .classpath(classpath)
                .type(PodFunctionBuild.class.getSimpleName())
                .build();
    }

    private String type;
    private String classpath;
    @Singular
    private List<ResourceTreeNode> dependencies;
}
