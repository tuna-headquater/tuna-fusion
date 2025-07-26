package ai.tuna.fusion.metadata.crd.podpool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodFunctionBuildStatus {

    @Data
    public static class DeployArchive {
        private PodFunction.FilesystemFolderSource filesystemFolderSource;
    }
    private DeployArchive deployArchive;

    public enum Phase {
        Pending,
        Scheduled,
        Running,
        Succeeded,
        Failed
    }

    @Data
    public static class JobPodInfo {
        private String podName;
        private String podPhase;
        private String logs;
    }

    private Phase phase;
    private JobPodInfo jobPod;
}
