package ai.tuna.fusion.kubernetes.operator.support;

import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.List;

/**
 * @author robinqu
 */
public interface InitContainerCommand {
    List<String> renderInitCommand();
    List<EnvVar> renderFileAssetsEnvVars();
}
