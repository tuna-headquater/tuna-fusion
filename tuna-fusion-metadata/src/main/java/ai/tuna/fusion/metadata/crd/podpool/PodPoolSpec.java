package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.kubernetes.api.model.PodSpec;
import lombok.Data;

import static ai.tuna.fusion.metadata.crd.podpool.PodPool.TTL_IN_SECONDS_FOR_SPECIALIZED_POD;

/**
 * @author robinqu
 */
@Data
@ValidationRule(value = "has(self.runtimePodSpec) || has(self.runtimeImage)")
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
    @Required
    @Min(1)
    private int poolSize = 3;

    @Required
    @Min(1)
    private int runPerPod = 3;

    /**
     * The time to live of each pod in seconds.
     */
    @Required
    @Min(60)
    private long ttlPerPod = TTL_IN_SECONDS_FOR_SPECIALIZED_POD;

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
    @Required
    private String archivePvcName;

}
