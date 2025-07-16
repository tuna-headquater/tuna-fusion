package ai.tuna.fusion.kubernetes.operator.support.impl;

import ai.tuna.fusion.kubernetes.operator.support.InitContainerCommand;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author robinqu
 */
public abstract class BaseInitContainerCommand implements InitContainerCommand {

    private final Map<String, PodFunction.FileAsset> fileAssets;

    public BaseInitContainerCommand(Map<String, PodFunction.FileAsset> fileAssets) {
        this.fileAssets = fileAssets;
        configureFileAssets(this.fileAssets);
    }

    abstract void configureFileAssets(Map<String, PodFunction.FileAsset> fileAssets);

    @Override
    public List<String> renderInitCommand() {
        var script = fileAssets.entrySet().stream().map(entry -> {
            var line = "echo -e '%s' > %s".formatted(entry.getValue().getContent(), entry.getKey());
            if (entry.getValue().isExecutable()) {
                line += " && chmod +x %s".formatted(entry.getKey());
            }
            return line;
        }).collect(Collectors.joining(" && "));
        return Arrays.asList("sh", "-c", script);
    }

    @Override
    public List<EnvVar> renderFileAssetsEnvVars() {
        return fileAssets.values().stream().map(s -> new EnvVarBuilder()
                .withName(convertToEnvName(s))
                .withValue(PodFunctionBuild.WORKSPACE_ROOT_PATH.resolve(s.getFileName()).toString())
                .build()
        ).toList();
    }

    private String convertToEnvName(PodFunction.FileAsset fileAsset) {
        return Optional.ofNullable(fileAsset.getEnvName())
                .orElseGet(()-> fileAsset.getFileName().toUpperCase().replaceAll("[^a-zA-Z0-9_]", "_") + "_PATH");
    }


}
