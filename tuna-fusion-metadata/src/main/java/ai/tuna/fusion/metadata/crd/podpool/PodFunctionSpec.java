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

}
