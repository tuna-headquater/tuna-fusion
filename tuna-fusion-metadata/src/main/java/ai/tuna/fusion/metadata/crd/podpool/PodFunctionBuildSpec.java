package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.PodSpec;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class PodFunctionBuildSpec {
    @Required
    private String sourceArchiveSubPath;
    private PodSpec overrideJobPodSpec;
}
