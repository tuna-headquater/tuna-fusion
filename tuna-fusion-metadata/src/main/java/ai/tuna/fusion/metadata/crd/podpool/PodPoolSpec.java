package ai.tuna.fusion.metadata.crd.podpool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.kubernetes.api.model.PodSpec;
import lombok.Data;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.*;

/**
 * @author robinqu
 */
@Data
@ValidationRule(value = "has(self.runtimePodSpec) || has(self.runtimeImage)")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodPoolSpec {
    /**
     * The image of the runtime pod.
     */
    @Required
    private String runtimeImage;

    /**
     * The image of the builder pod.
     */
    @Required
    private String builderImage;

    /**
     * The size of the pool.
     */
    @Min(1)
    private Integer poolSize = DEFAULT_POOL_SIZE;

    @Min(1)
    private Integer runPerPod = DEFAULT_RUN_PER_POD;

    /**
     * The time to live of each pod in seconds.
     */
    @Min(60)
    private Long ttlPerPod = TTL_IN_SECONDS_FOR_SPECIALIZED_POD;

    /**
     * The pod spec of the runtime pod.
     */
    private PodSpec runtimePodSpec;

    /**
     * The service account name of the runtime pod.
     */
    private String runtimePodServiceAccountName;

    /**
     * The service account name of the builder pod.
     */
    private String builderPodServiceAccountName;

    /**
     * The optional build script, which will be placed in `build_source.sh` in Job pod of the builder.
     */
    private String buildScript;

    /**
     * The name of PVC to store build archives.
     */
    private String archivePvcName;

}
