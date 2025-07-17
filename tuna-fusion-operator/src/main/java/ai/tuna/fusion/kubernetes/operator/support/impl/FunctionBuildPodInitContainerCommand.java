package ai.tuna.fusion.kubernetes.operator.support.impl;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.computeDeployFileAssetPath;

/**
 * @author robinqu
 */
public class FunctionBuildPodInitContainerCommand extends BaseInitContainerCommand {
    private final PodFunction podFunction;
    private final PodFunctionBuild podFunctionBuild;
    private final PodPool podPool;

    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper objectMapper;

    public FunctionBuildPodInitContainerCommand(
            PodFunctionBuild podFunctionBuild,
            PodFunction podFunction,
            PodPool podPool
    ) {
        super(new HashMap<>(32));
        this.objectMapper = new ObjectMapper();
        this.podPool = podPool;
        this.podFunction = podFunction;
        this.podFunctionBuild = podFunctionBuild;
    }

    @Override
    void configureFileAssets(Map<String, PodFunction.FileAsset> fileAssets) {
        fileAssets.put(PodFunctionBuild.BUILD_SOURCE_SCRIPT_FILENAME, renderBuildScript());
        fileAssets.put(PodFunctionBuild.SOURCE_ARCHIVE_MANIFEST, renderSourceArchiveJson());
        Optional.ofNullable(podFunction.getSpec().getFileAssets())
                .ifPresent(assets -> assets.forEach(fileAsset -> fileAssets.put(fileAsset.getFileName(), fileAsset)));
        Optional.ofNullable(podFunctionBuild.getSpec().getAdditionalFileAssets())
                .ifPresent(assets -> assets.forEach(fileAsset -> fileAssets.put(fileAsset.getFileName(), fileAsset)));
    }

    @SneakyThrows
    private PodFunction.FileAsset renderSourceArchiveJson() {
        return PodFunction.FileAsset.builder()
                .fileName(PodFunctionBuild.SOURCE_ARCHIVE_MANIFEST)
                .targetDirectory(PodFunction.TargetDirectory.WORKSPACE)
                .content(objectMapper.writeValueAsString(podFunctionBuild.getSpec().getSourceArchive()))
                .executable(false)
                .build();
    }

    @Override
    protected String pathForFileAsset(PodFunction.FileAsset fileAsset) {
        return switch(fileAsset.getTargetDirectory()) {
            case WORKSPACE -> PodFunctionBuild.WORKSPACE_ROOT_PATH.resolve(fileAsset.getFileName()).toString();
            case DEPLOY_ARCHIVE -> computeDeployFileAssetPath(podFunctionBuild.getMetadata().getUid(), fileAsset);
        };
    }

    private PodFunction.FileAsset renderBuildScript() {
        return PodFunction.FileAsset.builder()
                .fileName(PodFunctionBuild.BUILD_SOURCE_SCRIPT_FILENAME)
                .targetDirectory(PodFunction.TargetDirectory.WORKSPACE)
                .content(
                        Optional.ofNullable(podFunctionBuild.getSpec().getEnvironmentOverrides())
                        .map(PodFunctionBuildSpec.EnvironmentOverrides::getBuildScript)
                        .orElse(podPool.getSpec().getBuildScript())
                )
                .executable(true)
                .build();
    }

}
