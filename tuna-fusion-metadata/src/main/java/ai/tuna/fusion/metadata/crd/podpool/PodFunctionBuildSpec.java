package ai.tuna.fusion.metadata.crd.podpool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.generator.annotation.Required;
import lombok.Data;

import java.util.List;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodFunctionBuildSpec {

    @Data
    public static class SourceArchive {
        private PodFunction.HttpZipSource httpZipSource;
        private PodFunction.FilesystemZipSource filesystemZipSource;
        private PodFunction.FilesystemFolderSource filesystemFolderSource;
    }

    @Required
    private SourceArchive sourceArchive;

    @Data
    public static class EnvironmentOverrides {
        private String buildScript;
        private String builderImage;
        private String serviceAccountName;
    }
    private EnvironmentOverrides environmentOverrides;
    private List<PodFunction.FileAsset> additionalFileAssets;
    private String podFunctionName;
}
