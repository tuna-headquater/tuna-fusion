package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ai.tuna.fusion.gitops.server.git.PipelineUtils.logError;
import static ai.tuna.fusion.gitops.server.git.PipelineUtils.logInfo;

/**
 * @author robinqu
 */
@Slf4j
public abstract class BaseArchiveHandler implements SourceArchiveHandler {

    @Override
    public RevTree filterCommands(RevWalk revWalk, String branchName, Collection<ReceiveCommand> commands) throws IOException {
        var filteredCommands = commands.stream()
                .filter(cmd -> cmd.getType() == ReceiveCommand.Type.UPDATE || cmd.getType() == ReceiveCommand.Type.CREATE)
                .filter(cmd -> Strings.CS.equals(cmd.getRefName(), branchName))
                .filter(cmd -> !cmd.getNewId().equals(ObjectId.zeroId()))
                .toList();
        if (filteredCommands.isEmpty()) {
            throw new IllegalStateException("No valid commands for default branch found");
        }
        log.info("[filterCommands] {} commands selected", filteredCommands.size());

        // Use the first valid command's commit
        ReceiveCommand cmd = filteredCommands.getFirst();
        ObjectId commitId = cmd.getNewId();
        RevCommit commit = revWalk.parseCommit(commitId);
        RevTree tree = commit.getTree();
        log.debug("[filterCommands] Using tree from commit: {}", commitId.getName());
        return tree;
    }

    protected void createLocalSnapshotFolder(
            ReceivePack rp,
            Collection<ReceiveCommand> commands,
            AgentDeploymentSpec.GitOptions gitOptions,
            Path destinationPath
            ) throws IOException {

        try (Repository repo = rp.getRepository()) {

            try (Git git = Git.cloneRepository()
                    .setURI(repo.getDirectory().toURI().toString())
                    .setDirectory(destinationPath.toFile())
                    .setDepth(1)
                    .setBranch(gitOptions.getWatchedBranchName())
                    .call()) {
                // Update submodules in the temp clone
                git.submoduleInit().call();
                git.submoduleUpdate().setFetch(true).call();
            }

            // clean .git folder
            FileUtils.deleteDirectory(destinationPath.resolve(".git").toFile());


//
//            logInfo(rp, "Creating local snapshot folder: %s", destinationPath);
//            addRepoToFolder(repo, destinationPath, "", gitOptions.getWatchedBranchName());
//            try (SubmoduleWalk subWalk = SubmoduleWalk.forIndex(repo)) {
//                while (subWalk.next()) {
//                    String subPath = subWalk.getPath();
//                    try (Repository subRepo = subWalk.getRepository()) {
//                        logInfo(rp, "Adding submodule: {}", subPath);
//                        addRepoToFolder(subRepo, destinationPath, subPath + "/", "HEAD");
//                    }
//                }
//            }
            logInfo(rp, "Snapshot created: " + destinationPath);
        } catch (Exception e) {
            throw new IOException("Snapshot failed", e);
        }
    }


//    private void addRepoToFolder(Repository repo,
//                                 Path destinationPath,
//                                 String subPath,
//                                 String ref) throws IOException {
//        ObjectId head = repo.resolve(ref);
//        if (head == null) {
//            throw new IOException("No HEAD reference found in repository for GitDir=" + repo.getDirectory() + ", subPath=" + subPath);
//        }
//        try (RevWalk revWalk = new RevWalk(repo);
//             TreeWalk treeWalk = new TreeWalk(repo)) {
//
//            RevTree tree = revWalk.parseTree(head);
//            treeWalk.addTree(tree);
//            treeWalk.setRecursive(true);
//
//            while (treeWalk.next()) {
//                var entryPath = subPath + treeWalk.getPathString();
//                if (shouldInclude(entryPath)) {
//                    log.debug("[addRepoToFolder] Processing {}", entryPath);
//                    var targetPath = destinationPath.resolve(entryPath);
//                    Files.createDirectories(targetPath.getParent());
//                    ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
//                    try(var os = Files.newOutputStream(targetPath)) {
//                        loader.copyTo(os);
//                    }
//                } else {
//                    log.debug("[addRepoToFolder] Skipping {}", entryPath);
//                }
//            }
//        }
//    }
//
//    protected boolean shouldInclude(String entryPath) {
//        return entryPath.startsWith(".git");
//    }

}
