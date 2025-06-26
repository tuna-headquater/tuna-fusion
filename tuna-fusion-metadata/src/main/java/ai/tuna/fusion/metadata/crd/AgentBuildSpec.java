package ai.tuna.fusion.metadata.crd;

import lombok.*;

/**
 * @author robinqu
 */
@Data
public class AgentBuildSpec {
    enum SourcePackageProvider {
        S3,
        Fission
    }
    public static class SourcePackageResource {
        private SourcePackageProvider provider;
        private String resourceLocation;
    }
    private SourcePackageResource sourcePackageResource;
    private String buildScript;
    private String builderImage;
    private String serviceAccountName;
}
