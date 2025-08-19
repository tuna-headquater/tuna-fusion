package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;

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
    public PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch, String subPath) throws IOException {
        Repository repo = receivePack.getRepository();
        var destinationPath = archiveRootPath.resolve(UUID.randomUUID().toString());
        log.info("[createSourceArchive] defaultBranch={}, subPath={}, destinationPath={}", defaultBranch, subPath, destinationPath);

        try (RevWalk revWalk = new RevWalk(repo)) {
            var tree = filterCommands(revWalk, defaultBranch, commands);

            // Create a new TreeWalk to traverse the repository tree
            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.setRecursive(true);
                treeWalk.addTree(tree);

                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (path.startsWith(".git")) {
                        continue; // Skip .git directory
                    }

                    // If subPath is specified, only include files under that path
                    if (StringUtils.isNoneBlank(subPath) && !path.startsWith(subPath + "/") && !path.equals(subPath)) {
                        continue;
                    }

                    receivePack.sendMessage("Processing: " + path);
                    ObjectId objectId = treeWalk.getObjectId(0);
                    try (InputStream in = repo.open(objectId).openStream()) {
                        // Create target file path
                        Path targetFile = destinationPath.resolve(path);
                        // Ensure parent directories exist
                        if (targetFile.getParent() != null && !Files.exists(targetFile.getParent())) {
                            Files.createDirectories(targetFile.getParent());
                        }
                        // Write file content
                        Files.copy(in, targetFile);
                    }
                }
                log.debug("[createSourceArchive] Finished processing {} entries", treeWalk.getPathString());
            }
        }
        var sourceArchive = new PodFunctionBuildSpec.SourceArchive();
        var folderSource = new PodFunction.FilesystemFolderSource();
        folderSource.setPath(destinationPath.toString());
        sourceArchive.setFilesystemFolderSource(folderSource);
        return sourceArchive;
    }
}
