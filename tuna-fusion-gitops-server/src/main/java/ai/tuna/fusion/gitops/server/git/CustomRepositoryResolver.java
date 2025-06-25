package ai.tuna.fusion.gitops.server.git;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import java.io.File;

/**
 * @author robinqu
 */
@Slf4j
public class CustomRepositoryResolver extends FileResolver<HttpServletRequest> {
    // 100MB
    private static final long MAX_REPO_SIZE = 100 * 1024 * 1024;

    public CustomRepositoryResolver(File basePath) {
        super(basePath, true);
        log.info("CustomRepositoryResolver init with basePath: {}", basePath);
    }

    @Override
    public Repository open(HttpServletRequest req, String name) throws RepositoryNotFoundException, ServiceNotEnabledException {
        Repository repo = super.open(req, name);
        log.info("Resolving repo: name={}, req.path={}", name, req.getPathInfo());
        // 检查仓库大小
        long size = calculateRepoSize(repo.getDirectory());
        if (size > MAX_REPO_SIZE) {
            throw new ServiceNotEnabledException("Repository size exceeds limit");
        }
        return repo;
    }

    private long calculateRepoSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateRepoSize(file);
                }
            }
        }
        return size;
    }
}
