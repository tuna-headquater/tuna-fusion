package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.PodSpec;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class PodPoolSpec {
    @Required
    private String runtimeImage;
    @Required
    private String builderImage;
    @Required
    @Min(1)
    private int poolSize = 3;
    private PodSpec runtimePodSpec;
    private String runtimePodServiceAccountName;
    private String builderPodServiceAccountName;

    @Required
    private String buildScript;

    @Required
    private String archivePvcName;


    @Data
    public static class Endpoint {
        @Required
        String protocol = "https";
        @Required
        String externalHost;
    }
    @Required
    private Endpoint endpoint;

}
