package ai.tuna.fusion.gitops.server.spring.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author robinqu
 */
@ConfigurationProperties(prefix = "gitops")
@Data
public class GitOpsServerProperties {
    private File reposRootPath;

    {
        try {
            reposRootPath = Files.createTempDirectory("gitops").toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String defaultBranch = "refs/heads/master";
}
