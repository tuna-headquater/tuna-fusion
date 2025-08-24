package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

/**
 * @author robinqu
 */
@Slf4j
public class FolderSourceArchiveHandler extends BaseArchiveHandler {

    private final Path archiveRootPath;

    public FolderSourceArchiveHandler(Path archiveRootPath) {
        this.archiveRootPath = archiveRootPath;
    }

    @Override
    public PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, AgentDeploymentSpec.GitOptions gitOptions) throws IOException {
        var destinationPath = archiveRootPath.resolve(UUID.randomUUID().toString());
        log.info("[createSourceArchive] gitOptions={}, destinationPath={}", gitOptions, destinationPath);

        var tempDirectoryPath = Files.createTempDirectory(UUID.randomUUID().toString());
        var subPath = gitOptions.getSubPath();
        try {
            log.info("[createSourceArchive] createLocalSnapshotFolder: {}", tempDirectoryPath);
            createLocalSnapshotFolder(receivePack, commands, gitOptions, tempDirectoryPath);
            Path sourcePath = StringUtils.isNoneBlank(subPath) ? tempDirectoryPath.resolve(subPath) : tempDirectoryPath;
            log.info("[createSourceArchive] move from {} to {}", sourcePath, destinationPath);
            Files.move(tempDirectoryPath.resolve(subPath), destinationPath);
        } catch (Exception e) {
            throw new IOException("Failed to checkout and update submodules", e);
        } finally {
            Files.deleteIfExists(tempDirectoryPath);
        }

        var sourceArchive = new PodFunctionBuildSpec.SourceArchive();
        var folderSource = new PodFunction.FilesystemFolderSource();
        folderSource.setPath(destinationPath.toString());
        sourceArchive.setFilesystemFolderSource(folderSource);
        return sourceArchive;
    }
}
