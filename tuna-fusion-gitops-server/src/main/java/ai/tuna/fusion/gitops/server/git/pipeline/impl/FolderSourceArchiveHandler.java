package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
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
public class FolderSourceArchiveHandler implements SourceArchiveHandler {

    private final Path archiveRootPath;

    public FolderSourceArchiveHandler(Path archiveRootPath) {
        this.archiveRootPath = archiveRootPath;
    }

    @Override
    public PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch) throws IOException {
        Repository repo = receivePack.getRepository();
        var destinationPath = archiveRootPath.resolve(UUID.randomUUID().toString());
        log.info("Creating repository snapshot at: {}", destinationPath);

        try (RevWalk revWalk = new RevWalk(repo)) {
            var filteredCommands = commands.stream()
                    .filter(cmd -> cmd.getType() == ReceiveCommand.Type.UPDATE || cmd.getType() == ReceiveCommand.Type.CREATE)
                    .filter(cmd -> StringUtils.equals(cmd.getRefName(), defaultBranch))
                    .filter(cmd -> !cmd.getNewId().equals(ObjectId.zeroId()))
                    .toList();

            if (filteredCommands.isEmpty()) {
                throw new IllegalStateException("No valid commands for default branch found");
            }
            log.info("{} commands selected for repo {}", filteredCommands.size(), repo.getDirectory());

            // Use the first valid command's commit
            ReceiveCommand cmd = filteredCommands.getFirst();
            ObjectId commitId = cmd.getNewId();
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            log.debug("Using tree from commit: {}", commitId.getName());

            // Create a new TreeWalk to traverse the repository tree
            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.setRecursive(true);
                treeWalk.addTree(tree);

                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (path.startsWith(".git")) {
                        continue; // Skip .git directory
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
                log.debug("Finished processing {} entries", treeWalk.getPathString());
            }
        }
        var sourceArchive = new PodFunctionBuildSpec.SourceArchive();
        var folderSource = new PodFunction.FilesystemFolderSource();
        folderSource.setPath(destinationPath.toString());
        return sourceArchive;
    }
}
