package ai.tuna.fusion.metadata.crd.podpool;

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
public class PodPool extends CustomResource<PodPoolSpec, PodPoolStatus> implements Namespaced {

    public static final String GENERIC_POD_LABEL_NAME = "fusion.tuna.ai/is-generic-pod";
    public static final String SPECIALIZED_POD_LABEL_VALUE = "fusion.tuna.ai/is-specialized-pod";
    public static final String MANAGED_POD_POOL_LABEL_NAME = "fusion.tuna.ai/managed-by-pod-pool";
    public static final int DEFAULT_RUNTIME_SERVICE_PORT = 8080;

}
