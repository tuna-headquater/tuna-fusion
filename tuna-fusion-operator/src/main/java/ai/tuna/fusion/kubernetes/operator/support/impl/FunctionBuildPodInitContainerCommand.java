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
import java.util.stream.Stream;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.computeDeployArchivePath;
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
        renderBuildScript().ifPresent(buildScript -> {
            fileAssets.put(PodFunctionBuild.BUILD_SOURCE_SCRIPT_FILENAME, buildScript);
        });
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
    protected Stream<String> enhanceCommandLines(Stream<String> generatedCommands) {
        return Stream.concat(
                Stream.of("mkdir -p %s".formatted(computeDeployArchivePath(podFunctionBuild))),
                generatedCommands
        );
    }

    @Override
    protected String pathForFileAsset(PodFunction.FileAsset fileAsset) {
        return switch(fileAsset.getTargetDirectory()) {
            case WORKSPACE -> PodFunctionBuild.WORKSPACE_ROOT_PATH.resolve(fileAsset.getFileName()).toString();
            case DEPLOY_ARCHIVE -> computeDeployFileAssetPath(podFunctionBuild.getMetadata().getUid(), fileAsset);
        };
    }

    private Optional<PodFunction.FileAsset> renderBuildScript() {
        var buildScript = Optional.ofNullable(podFunctionBuild.getSpec().getEnvironmentOverrides())
                .map(PodFunctionBuildSpec.EnvironmentOverrides::getBuildScript)
                .orElse(podPool.getSpec().getBuildScript());
        return Optional.ofNullable(buildScript)
                .map(s -> {
                    return PodFunction.FileAsset.builder()
                            .fileName(PodFunctionBuild.BUILD_SOURCE_SCRIPT_FILENAME)
                            .targetDirectory(PodFunction.TargetDirectory.WORKSPACE)
                            .content(s)
                            .executable(true)
                            .build();
                });
    }

}
