package ai.tuna.fusion.kubernetes.operator.support.impl;

import ai.tuna.fusion.kubernetes.operator.support.InitContainerCommand;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.computeDeployArchivePath;

/**
 * @author robinqu
 */
public abstract class BaseInitContainerCommand implements InitContainerCommand {

    private final Map<String, PodFunction.FileAsset> fileAssets;

    public BaseInitContainerCommand(Map<String, PodFunction.FileAsset> fileAssets) {
        this.fileAssets = fileAssets;
    }

    abstract void configureFileAssets(Map<String, PodFunction.FileAsset> fileAssets);

    @Override
    public List<String> renderInitCommand() {
        var commands = generateFileAssets().values().stream().map(fileAsset -> {
            var filePath = pathForFileAsset(fileAsset);
            var line = "echo -e '%s' > %s".formatted(fileAsset.getContent(), filePath);
            if (fileAsset.isExecutable()) {
                line += " && chmod +x %s".formatted(filePath);
            }
            return line;
        });
        var script = enhanceCommandLines(commands).collect(Collectors.joining(" && "));
        return Arrays.asList("sh", "-c", script);
    }

    protected Stream<String> enhanceCommandLines(Stream<String> generatedCommands) {
        return generatedCommands;
    }

    protected abstract String pathForFileAsset(PodFunction.FileAsset fileAsset);


    private Map<String, PodFunction.FileAsset> generateFileAssets() {
        var copiedFileAssets = new HashMap<>(fileAssets);
        configureFileAssets(copiedFileAssets);
        return copiedFileAssets;
    }

    @Override
    public List<EnvVar> renderFileAssetsEnvVars() {
        return generateFileAssets().values().stream().map(s -> new EnvVarBuilder()
                .withName(convertToEnvName(s))
                .withValue(pathForFileAsset(s))
                .build()
        ).toList();
    }

    private String convertToEnvName(PodFunction.FileAsset fileAsset) {
        return Optional.ofNullable(fileAsset.getEnvName())
                .orElseGet(()-> fileAsset.getFileName().toUpperCase().replaceAll("[^a-zA-Z0-9_]", "_") + "_PATH");
    }


}
