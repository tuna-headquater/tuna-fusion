package ai.tuna.fusion.kubernetes.operator.support;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.List;

/**
 * @author robinqu
 */
public interface BuilderFileAssets {
    ConfigMap workspaceFileAssetsConfigMap();
    ConfigMap sourcePatchFileAssetsConfigMap();
    List<EnvVar> sourcePathEnvVars();
}
