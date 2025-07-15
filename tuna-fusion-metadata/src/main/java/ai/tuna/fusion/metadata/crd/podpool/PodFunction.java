package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import lombok.Data;

/**
 * @author robinqu
 */
@Group("fusion.tuna.ai")
@Version("v1")
@ShortNames({"pf"})
@AdditionalPrinterColumn(name = "CurrentBuild", jsonPath = ".status.currentBuild.name")
@AdditionalPrinterColumn(name = "EffectiveBuild", jsonPath = ".status.effectiveBuild.name")
public class PodFunction extends CustomResource<PodFunctionSpec, PodFunctionStatus> implements Namespaced {
    @Data
    public static class HttpZipSource {
        private String url;
        private String sha256Checksum;
    }

    @Data
    public static class FilesystemZipSource {
        private String path;
        private String sha256Checksum;
    }

    @Data
    public static class FilesystemFolderSource {
        private String path;
    }
}
