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
        fileAssets.put(PodFunctionBuild.BUILD_SCRIPT_FILENAME, renderBuildScript());
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
                .content(objectMapper.writeValueAsString(podFunctionBuild.getSpec().getSourceArchive()))
                .executable(false)
                .build();
    }

    private PodFunction.FileAsset renderBuildScript() {
        return PodFunction.FileAsset.builder()
                .fileName(PodFunctionBuild.BUILD_SCRIPT_FILENAME)
                .content(
                        Optional.ofNullable(podFunctionBuild.getSpec().getEnvironmentOverrides())
                        .map(PodFunctionBuildSpec.EnvironmentOverrides::getBuildScript)
                        .orElse(podPool.getSpec().getBuildScript())
                )
                .executable(true)
                .build();
    }

}
