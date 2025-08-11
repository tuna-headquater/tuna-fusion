package ai.tuna.fusion.gitops.server.spring.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * @author robinqu
 */
@ConfigurationProperties(prefix = "gitops")
@Data
public class GitOpsServerProperties {
    private Path reposRootPath;

    {
        try {
            reposRootPath = Files.createTempDirectory("gitops").toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String defaultBranch = "refs/heads/master";
    private Set<String> watchedNamespaces;

    @Data
    public static class SourceArchiveHandlerProperties {
        public enum Type {
            ZipArchiveOnLocalHttpServer,
//            ZipArchiveOnS3,
            FolderOnFilesystem,
//            ZipArchiveOnFilesystem
        }
        private Type type;

        @Data
        public static class ZipArchiveOnLocalHttpServerProperties {
            private Path zipRepositoryRoot;
            private String httpServerBaseUrl;
        }
        private ZipArchiveOnLocalHttpServerProperties zipArchiveOnLocalHttpServer;

        @Data
        public static class FolderOnFilesystemProperties {
            private Path localSourceArchiveRootPath;
        }
        private FolderOnFilesystemProperties folderOnFilesystem;
    }
    private SourceArchiveHandlerProperties sourceArchiveHandler;


}
