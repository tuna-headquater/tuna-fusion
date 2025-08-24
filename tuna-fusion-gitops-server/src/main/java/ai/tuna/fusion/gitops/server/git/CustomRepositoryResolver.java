package ai.tuna.fusion.gitops.server.git;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author robinqu
 */
@Slf4j
public class CustomRepositoryResolver implements RepositoryResolver<HttpServletRequest> {

    private final Path repositoriesRootPath;

    public CustomRepositoryResolver(
            Path repositoriesRootPath
            ) {
        this.repositoriesRootPath = repositoriesRootPath;
        log.info("CustomRepositoryResolver initialized with root path: {}", repositoriesRootPath.toAbsolutePath());
    }

    @Override
    public Repository open(HttpServletRequest req, String name) throws RepositoryNotFoundException, ServiceNotEnabledException {
        if (isUnreasonableName(name)) {
            throw new RepositoryNotFoundException(name);
        }
        var dir = repositoriesRootPath.resolve(name);
        var dirFile = dir.toFile();
        if (!Files.exists(dir)) {
            if (!dirFile.mkdirs()) {
                throw new RepositoryNotFoundException("Cannot create dir for new repo: " + dirFile);
            }
            dirFile.deleteOnExit();
            log.info("[open] Attempt to init Git at {}", dirFile);
            try (Git git = Git.init().setGitDir(dirFile).setBare(true).call()) {
                var repo = git.getRepository();
                repo.incrementOpen();
                log.info("Successfully opened repository: {} ", name);
                return repo;
            } catch (GitAPIException e) {
                log.error("Error opening repository: {}", name, e);
                throw new ServiceNotEnabledException("Error opening repository: " + name, e);
            }
        }

        try {
            log.info("[open] Attempt to re-open Git at {}", dirFile);
            return RepositoryCache.open(
                    RepositoryCache.FileKey.exact(dirFile, FS.DETECTED),
                    true
            );
        } catch (IOException e) {
            throw new ServiceNotEnabledException("Cannot open repo at " + dir, e);
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
