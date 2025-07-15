package ai.tuna.fusion.gitops.server.git.pipeline;

import java.util.List;

/**
 * @author robinqu
 */
public interface ContainerInitScript {
    List<String> render();
}
