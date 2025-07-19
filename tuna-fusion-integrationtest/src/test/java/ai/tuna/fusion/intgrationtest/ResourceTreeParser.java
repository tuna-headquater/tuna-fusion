package ai.tuna.fusion.intgrationtest;

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * @author robinqu
 */
public class ResourceTreeParser {
    public static final ResourceTreeParser DEFAULT = new ResourceTreeParser();

    public ResourceTreeNode parse(String definitions) {
        // Input validation
        if (StringUtils.isBlank(definitions)) {
            throw new IllegalArgumentException("The YAML definitions must not be null or empty.");
        }
        // Custom constructor for ResourceTreeNode with explicit handling of complex types
        Yaml yaml = new Yaml(new Constructor(ResourceTreeNode.class, new LoaderOptions()));

        try {
            // Parse and return the constructed ResourceTreeNode
            return yaml.load(definitions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YAML definitions.", e);
        }
    }


}
