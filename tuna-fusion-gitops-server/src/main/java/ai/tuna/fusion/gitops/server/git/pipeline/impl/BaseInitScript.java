package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.pipeline.ContainerInitScript;
import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author robinqu
 */
public abstract class BaseInitScript implements ContainerInitScript {

    @Getter
    @Builder(toBuilder = true)
    public static class FileAsset {
        @Builder.Default
        private boolean executable = false;
        private String content;

    }

    private final Map<String, FileAsset> fileAssets;

    public BaseInitScript(Map<String, FileAsset> fileAssets) {
        this.fileAssets = fileAssets;
        configureFileAssets(this.fileAssets);
    }

    abstract void configureFileAssets(Map<String, FileAsset> fileAssets);

    @Override
    public List<String> render() {
        var script = fileAssets.entrySet().stream().map(entry -> {
            var line = "echo -e '%s' > %s".formatted(entry.getValue().getContent(), entry.getKey());
            if (entry.getValue().isExecutable()) {
                line += " && chmod +x %s".formatted(entry.getKey());
            }
            return line;
        }).collect(Collectors.joining(" && "));
        return Arrays.asList("sh", "-c", script);
    }
}
