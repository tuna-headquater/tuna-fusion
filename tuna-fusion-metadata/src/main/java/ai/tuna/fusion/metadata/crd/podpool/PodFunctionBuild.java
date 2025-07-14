package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumns;
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
@ShortNames({"pfb"})
@AdditionalPrinterColumns({
        @AdditionalPrinterColumn(name = "Phase", jsonPath = ".status.phase"),
        @AdditionalPrinterColumn(name = "JobPodName", jsonPath = ".status.jobPod.podName"),
})
public class PodFunctionBuild extends CustomResource<PodFunctionBuildSpec, PodFunctionBuildStatus> implements Namespaced {

}
