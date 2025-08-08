package ai.tuna.fusion.executor.driver.podpool.impl;

import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import lombok.Builder;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
@Builder
public class PodSpecializeRequest {
    private PodFunctionBuildStatus.DeployArchive deployArchive;
    private String entrypoint;
    private PodFunctionSpec.AppType appType;
}
