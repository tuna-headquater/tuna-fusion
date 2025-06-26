package ai.tuna.fusion.gitops.server.git;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * @author robinqu
 */
@Slf4j
public class CustomRepositoryResolver implements RepositoryResolver<HttpServletRequest> {
    // 100MB
    private static final long MAX_REPO_SIZE = 100 * 1024 * 1024;

    private final File repositoriesRootPath;

    public CustomRepositoryResolver(
            File repositoriesRootPath
            ) {
        this.repositoriesRootPath = repositoriesRootPath;
        log.info("CustomRepositoryResolver initialized with root path: {}", repositoriesRootPath.getAbsolutePath());
    }

    @Override
    public Repository open(HttpServletRequest req, String name) throws RepositoryNotFoundException, ServiceNotAuthorizedException, ServiceNotEnabledException, ServiceMayNotContinueException {
        if (isUnreasonableName(name)) {
            throw new RepositoryNotFoundException(name);
        }
        File dir = repositoriesRootPath.toPath().resolve(UUID.randomUUID().toString()).resolve(name).toFile();
        if (!dir.mkdirs()) {
            throw new ServiceNotEnabledException("Failed to create temp repository");
        }
        dir.deleteOnExit();
        try (Git git = Git.init().setDirectory(dir).call()) {
            // 打开仓库
            var repo = git.getRepository();
            repo.incrementOpen();
            log.info("Successfully opened repository: {} ", name);
            return repo;
        } catch (GitAPIException e) {
            log.error("Error opening repository: {}", name, e);
            throw new RepositoryNotFoundException("Error opening repository: " + name, e);
        }
    }

    private boolean isUnreasonableName(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }

        if (name.indexOf('\\') >= 0 || new File(name).isAbsolute()) {
            return true;
        }

        return name.startsWith("../") || name.contains("/../") || name.contains("/./") || name.contains("//");
    }

}
