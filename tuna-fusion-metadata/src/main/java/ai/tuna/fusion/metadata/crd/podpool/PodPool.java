package ai.tuna.fusion.metadata.crd.podpool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * @author robinqu
 */
@Group("fusion.tuna.ai")
@Version("v1")
@ShortNames({"pp"})
@AdditionalPrinterColumn(name = "PoolSize", jsonPath = ".spec.poolSize")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodPool extends CustomResource<PodPoolSpec, PodPoolStatus> implements Namespaced {

    public static final String GENERIC_POD_LABEL_NAME = "fusion.tuna.ai/is-generic-pod";
    public static final String SPECIALIZED_POD_LABEL_VALUE = "fusion.tuna.ai/is-specialized-pod";
    public static final String SPECIALIZED_POD_FUNCTION_NAME_LABEL_VALUE = "fusion.tuna.ai/specialized-function-name";
    public static final String SPECIALIZED_POD_FUNCTION_BUILD_ID_LABEL_VALUE = "fusion.tuna.ai/specialized-function-build-id";
    public static final String POD_POOL_NAME_LABEL_NAME = "fusion.tuna.ai/pool-name";
    public static final int DEFAULT_RUNTIME_SERVICE_PORT = 8888;
    public static final String DR_SELECTOR = "fusion.tuna.ai/managed-by-pp";
    public static final long TTL_IN_SECONDS_FOR_SPECIALIZED_POD = 60 * 60 * 24;
    public static final int DEFAULT_RUN_PER_POD = 10;
    public static final int DEFAULT_POOL_SIZE = 5;
    public static final int POD_ACCESS_PER_BUILD = 3;
}
