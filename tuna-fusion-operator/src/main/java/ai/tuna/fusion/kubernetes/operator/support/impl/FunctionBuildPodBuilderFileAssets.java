package ai.tuna.fusion.kubernetes.operator.support.impl;

import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodFunctionBuildReconciler;
import ai.tuna.fusion.kubernetes.operator.support.BuilderFileAssets;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.*;
import static ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild.SOURCE_PATCH_PATH;

/**
 * @author robinqu
 */
public class FunctionBuildPodBuilderFileAssets implements BuilderFileAssets {
    private final PodFunction podFunction;
    private final PodFunctionBuild podFunctionBuild;
    private final PodPool podPool;
    private final List<PodFunction.FileAsset> fileAssets;

    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper objectMapper;

    public FunctionBuildPodBuilderFileAssets(
            PodFunctionBuild podFunctionBuild,
            PodFunction podFunction,
            PodPool podPool
    ) {
        this.fileAssets = new ArrayList<>();
        this.objectMapper = new ObjectMapper();
        this.podPool = podPool;
        this.podFunction = podFunction;
        this.podFunctionBuild = podFunctionBuild;
        configureFileAssets();
    }

    void configureFileAssets() {
        fileAssets.add(patchSourceScript());
        renderBuildScript().ifPresent(fileAssets::add);
        fileAssets.add(renderSourceArchiveJson());
        Optional.ofNullable(podFunction.getSpec().getFileAssets())
                .ifPresent(fileAssets::addAll);
        Optional.ofNullable(podFunctionBuild.getSpec().getAdditionalFileAssets())
                .ifPresent(fileAssets::addAll);
    }

    public PodFunction.FileAsset patchSourceScript() {
        var commands = fileAssets.stream().filter(fileAsset -> fileAsset.getTargetDirectory() == PodFunction.TargetDirectory.DEPLOY_ARCHIVE).map(fileAsset -> {
            var sourcePath = SOURCE_PATCH_PATH.resolve(fileAsset.getFileName());
            var targetPath = computeDeployFileAssetPath(podFunctionBuild.getMetadata().getUid(), fileAsset);
            var line = "cp -f %s %s".formatted(sourcePath, targetPath);
            if (fileAsset.isExecutable()) {
                line += " && chmod +x %s".formatted(targetPath);
            }
            return line;
        });
        var headline = """
                #!/usr/bin/env bash
                set -ex
                
                """;
        var script = headline + enhanceCommandLines(commands).collect(Collectors.joining("\n"));
        return PodFunction.FileAsset.builder()
                .targetDirectory(PodFunction.TargetDirectory.WORKSPACE)
                .content(script)
                .fileName(PodFunctionBuild.PATCH_SOURCE_SCRIPT_FILENAME)
                .build();
    }


    @Override
    public List<EnvVar> sourcePathEnvVars() {
        return fileAssets.stream()
                .filter(s -> s.getTargetDirectory() == PodFunction.TargetDirectory.DEPLOY_ARCHIVE)
                .map(s -> new EnvVarBuilder()
                .withName(convertToEnvName(s))
                .withValue(computeDeployFileAssetPath(podFunctionBuild.getMetadata().getUid(), s))
                .build()
        ).toList();
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

    private Stream<String> enhanceCommandLines(Stream<String> generatedCommands) {
        var ns = podFunction.getMetadata().getNamespace();
        var deployArchivePath = computeDeployArchivePath(podFunctionBuild);
        var configmapCommands = Optional.ofNullable(podFunction.getSpec().getConfigmaps())
                .map(configMaps -> configMaps.stream().map(configmapReference -> "(cp -r /configmaps/%s/%s %s/configmaps 2>/dev/null || echo 'configmap absent; skip...')".formatted(ns, configmapReference.getName(), deployArchivePath)).toList()).stream().flatMap(Collection::stream);
        var secretCommands = Optional.ofNullable(podFunction.getSpec().getSecrets())
                .map(secrets -> secrets.stream().map(secretReference -> "(cp -r /secrets/%s/%s %s/secrets 2>/dev/null || echo 'secret absent; skip...')".formatted(ns, secretReference.getName(), deployArchivePath)).toList()).stream().flatMap(Collection::stream);

        return Stream.of(
                // create dirs
                Stream.of(
                        "mkdir -p %s".formatted(deployArchivePath)
                ),
                // write file assets in PF
                generatedCommands,
                // copy configmaps
                configmapCommands,
                // copy secrets
                secretCommands
        ).flatMap(Function.identity());
    }


    private Optional<PodFunction.FileAsset> renderBuildScript() {
        var buildScript = Optional.ofNullable(podFunctionBuild.getSpec().getEnvironmentOverrides())
                .map(PodFunctionBuildSpec.EnvironmentOverrides::getBuildScript)
                .orElse(podPool.getSpec().getBuildScript());
        return Optional.ofNullable(buildScript)
                .map(s -> PodFunction.FileAsset.builder()
                        .fileName(PodFunctionBuild.BUILD_SOURCE_SCRIPT_FILENAME)
                        .targetDirectory(PodFunction.TargetDirectory.WORKSPACE)
                        .content(s)
                        .executable(true)
                        .build());
    }

    @Override
    public ConfigMap workspaceFileAssetsConfigMap() {
        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(workspaceConfigMapName(podFunctionBuild))
                .withNamespace(podFunctionBuild.getMetadata().getNamespace())
                .addNewOwnerReference()
                .withKind(HasMetadata.getKind(PodFunctionBuild.class))
                .withName(podFunctionBuild.getMetadata().getName())
                .withApiVersion(HasMetadata.getApiVersion(PodFunctionBuild.class))
                .withController(true)
                .withBlockOwnerDeletion(false)
                .endOwnerReference()
                .addToLabels(PodFunctionBuildReconciler.SELECTOR, "true")
                .endMetadata()
                .withData(fileAssetData(PodFunction.TargetDirectory.WORKSPACE))
                .build();
    }

    @Override
    public ConfigMap sourcePatchFileAssetsConfigMap() {
        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(sourcePathConfigMapName(podFunctionBuild))
                .withNamespace(podFunctionBuild.getMetadata().getNamespace())
                .addNewOwnerReference()
                .withKind(HasMetadata.getKind(PodFunctionBuild.class))
                .withName(podFunctionBuild.getMetadata().getName())
                .withApiVersion(HasMetadata.getApiVersion(PodFunctionBuild.class))
                .withController(true)
                .withBlockOwnerDeletion(false)
                .endOwnerReference()
                .addToLabels(PodFunctionBuildReconciler.SELECTOR, "true")
                .endMetadata()
                .withData(fileAssetData(PodFunction.TargetDirectory.DEPLOY_ARCHIVE))
                .build();
    }

    protected Map<String, String> fileAssetData(PodFunction.TargetDirectory targetDirectory) {
        return fileAssets.stream().filter(s -> s.getTargetDirectory() == targetDirectory).collect(Collectors.toMap(PodFunction.FileAsset::getFileName, PodFunction.FileAsset::getContent));
    }

    private String convertToEnvName(PodFunction.FileAsset fileAsset) {
        return Optional.ofNullable(fileAsset.getEnvName())
                .orElseGet(()-> fileAsset.getFileName().toUpperCase().replaceAll("[^a-zA-Z0-9_]", "_") + "_PATH");
    }
}
