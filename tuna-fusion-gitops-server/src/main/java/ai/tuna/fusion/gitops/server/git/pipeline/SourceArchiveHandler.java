package ai.tuna.fusion.gitops.server.git.pipeline;

import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.util.IO;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author robinqu
 */
public interface SourceArchiveHandler {

    PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch) throws IOException;

    RevTree filterCommands(RevWalk revWalk, String branchName, Collection<ReceiveCommand> commands) throws IOException;

}
