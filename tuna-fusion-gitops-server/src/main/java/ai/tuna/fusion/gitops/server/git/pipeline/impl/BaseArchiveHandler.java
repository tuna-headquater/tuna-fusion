package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.Collection;

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
}
