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
@ShortNames({"pf"})
@AdditionalPrinterColumn(name = "CurrentBuild", jsonPath = ".status.currentBuild.name")
@AdditionalPrinterColumn(name = "EffectiveBuild", jsonPath = ".status.effectiveBuild.name")
public class PodFunction extends CustomResource<PodFunctionSpec, PodFunctionStatus> implements Namespaced {
}
