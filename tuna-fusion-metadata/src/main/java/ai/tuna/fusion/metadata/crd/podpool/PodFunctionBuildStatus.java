package ai.tuna.fusion.metadata.crd.podpool;

import lombok.Data;

/**
 * @author robinqu
 */
@Data
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
