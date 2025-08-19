package ai.tuna.fusion.gitops.server.git.pipeline;

import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import java.io.IOException;
import java.util.Collection;

/**
 * @author robinqu
 */
public interface SourceArchiveHandler {

    PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch, String subPath) throws IOException;

    RevTree filterCommands(RevWalk revWalk, String branchName, Collection<ReceiveCommand> commands) throws IOException;

}
