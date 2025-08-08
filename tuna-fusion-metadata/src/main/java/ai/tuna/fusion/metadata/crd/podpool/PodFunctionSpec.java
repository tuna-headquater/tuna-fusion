package ai.tuna.fusion.metadata.crd.podpool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.generator.annotation.Required;
import lombok.*;

import java.util.List;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodFunctionSpec {

    @Required
    private String entrypoint;

    private List<PodFunction.FileAsset> fileAssets;

    @Required
    private String podPoolName;

    public enum AppType {
        WebApp,
        AgentApp
    }
    @Required
    private AppType appType = AppType.AgentApp;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConfigmapReference {
        private String namespace;
        private String name;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SecretReference {
        private String namespace;
        private String name;
    }

    private List<ConfigmapReference> configmaps;
    private List<SecretReference> secrets;

}
