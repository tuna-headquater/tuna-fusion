package ai.tuna.fusion.metadata.crd;

import lombok.*;

/**
 * @author robinqu
 */
@Data
public class AgentBuildSpec {
    public enum SourcePackageProvider {
        S3,
        Fission
    }
    @Data
    public static class SourcePackageResource {
        private SourcePackageProvider provider;
        private String resourceId;
        private String sha256Checksum;
    }
    private SourcePackageResource sourcePackageResource;
    private String buildScript;
    private String builderImage;
    private String serviceAccountName;
}
