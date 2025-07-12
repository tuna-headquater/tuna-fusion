package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
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
public class PodPool extends CustomResource<PodPoolSpec, PodPoolStatus> {

}
