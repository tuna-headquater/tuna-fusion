package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumns;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

import java.nio.file.Path;

/**
 * @author robinqu
 */
@Group("fusion.tuna.ai")
@Version("v1")
@ShortNames({"pfb"})
@AdditionalPrinterColumns({
        @AdditionalPrinterColumn(name = "PodFunction", jsonPath = ".spec.podFunctionName"),
        @AdditionalPrinterColumn(name = "Phase", jsonPath = ".status.phase"),
        @AdditionalPrinterColumn(name = "JobPodName", jsonPath = ".status.jobPod.podName"),
})
public class PodFunctionBuild extends CustomResource<PodFunctionBuildSpec, PodFunctionBuildStatus> implements Namespaced {
    public static final Path ARCHIVE_ROOT_PATH = Path.of("/archive");
    public static final Path WORKSPACE_ROOT_PATH = Path.of("/workspace");
    public static final String BUILD_SCRIPT_FILENAME = "build.sh";
    public static final String AGENT_CARD_FILENAME = "agent_card.json";
    public static final String A2A_RUNTIME_FILENAME = "a2a_runtime.json";
    public static final String SOURCE_ARCHIVE_MANIFEST = "source_archive.json";



}
