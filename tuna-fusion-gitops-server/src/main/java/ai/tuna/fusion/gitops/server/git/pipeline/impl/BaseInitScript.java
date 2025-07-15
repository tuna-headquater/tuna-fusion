package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.pipeline.ContainerInitScript;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author robinqu
 */
public abstract class BaseInitScript implements ContainerInitScript {

    private final Map<String, String> fileAssets;

    public BaseInitScript(Map<String, String> fileAssets) {
        this.fileAssets = fileAssets;
        configureFileAssets(this.fileAssets);
    }

    abstract void configureFileAssets(Map<String, String> fileAssets);

    @Override
    public List<String> render() {
        var script = fileAssets.entrySet().stream().map(entry ->
                "echo -e '%s' > %s".formatted(entry.getValue(), entry.getKey())
        ).collect(Collectors.joining(" && "));
        return Arrays.asList("sh", "-c", script);
    }
}
