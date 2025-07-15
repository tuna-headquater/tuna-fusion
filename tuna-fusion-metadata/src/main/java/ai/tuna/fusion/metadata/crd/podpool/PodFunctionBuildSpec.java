package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.generator.annotation.Required;
import lombok.Data;

import java.util.List;

/**
 * @author robinqu
 */
@Data
public class PodFunctionBuildSpec {

    @Data
    public static class SourceArchive {
        private PodFunction.HttpZipSource httpZipSource;
        private PodFunction.FilesystemZipSource filesystemZipSource;
        private PodFunction.FilesystemFolderSource filesystemFolderSource;
    }

    @Required
    private SourceArchive sourceArchive;

    @Required
    private List<String> initContainerCommands;
//    private PodSpec overrideJobPodSpec;
}
