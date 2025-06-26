package ai.tuna.fusion.gitops.server.spring.property;

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

    @Data
    public static class S3Properties {
        private String endpointUrl;
        private String accessKey;
        private String accessSecret;
    }
    private S3Properties s3Properties;

}
