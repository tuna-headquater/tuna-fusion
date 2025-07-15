package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.generator.annotation.Required;
import lombok.Data;

import java.util.List;

/**
 * @author robinqu
 */
@Data
public class PodFunctionSpec {

    @Required
    private String entrypoint;
}
