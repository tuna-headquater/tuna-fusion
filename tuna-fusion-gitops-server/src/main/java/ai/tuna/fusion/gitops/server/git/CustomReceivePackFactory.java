package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.gitops.server.spring.GitRequestContextUtil;
import ai.tuna.fusion.metadata.crd.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import java.io.IOException;
import java.util.Collection;

/**
 * @author robinqu
 */
@Slf4j
public class CustomReceivePackFactory implements ReceivePackFactory<HttpServletRequest> {

    private final KubernetesClient kubernetesClient;

    public CustomReceivePackFactory(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public ReceivePack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        GitRequestContextUtil.initializeRequestAttributes(kubernetesClient, req);
        var agentDeployment = GitRequestContextUtil.getAgentDeployment().orElseThrow(()-> new ServiceNotEnabledException("AgentDeployment is not associated with this Git repository URL."));
        var agentCatalogue = GitRequestContextUtil.getAgentCatalogue().orElseThrow(()-> new ServiceNotEnabledException("AgentCatalogue is associated with this Git repository URL."));
        var agentEnvironment = GitRequestContextUtil.getAgentEnvironment().orElseThrow(()-> new ServiceNotEnabledException("AgentEnvironment is associated with this Git repository URL."));
        // TODO check permissions

        ReceivePack receivePack = new ReceivePack(db);
        receivePack.setPreReceiveHook((rp, commands) -> {
            handlePreReceiveHook(agentEnvironment, agentDeployment, agentCatalogue, req, receivePack, commands);
        });
        return receivePack;
    }

    private void handlePreReceiveHook(AgentEnvironment agentEnvironment, AgentDeployment agentDeployment, AgentCatalogue agentCatalogue, HttpServletRequest req, ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        try {
            var zipPath = PipelineUtils.createRepoZip(receivePack, commands, agentDeployment.getSpec().getGit().getWatchedBranchName());
            logInfo(receivePack, "📦 Snapshot for repository is created successfully: %s", zipPath);

            var sha256 = PipelineUtils.getSha256Checksum(zipPath);
            logInfo(receivePack, "🔢 SHA256 for snapshot: %s", sha256);

            var archiveId = PipelineUtils.fissionArchiveUpload(zipPath);
            logInfo(receivePack, "⏫ Archive ID for snapshot: %s", archiveId);

            var agentBuild = PipelineUtils.createAgentBuild(kubernetesClient, agentDeployment, agentEnvironment, archiveId, sha256);

            logInfo(receivePack, "💾 AgentBuild CR is created successfully: %s", agentBuild.getMetadata().getName());

            var podInfo = PipelineUtils.waitForJobPod(kubernetesClient, agentBuild.getMetadata().getName(), agentBuild.getMetadata().getNamespace());
            logInfo(receivePack, "⚒️ Job pod is created successfully: %s", podInfo.getPodName());

            PipelineUtils.streamPodLogs(kubernetesClient,
                    podInfo.getPodName(),
                    agentDeployment.getMetadata().getNamespace(),
                    line -> logInfo(receivePack, line)
            );

            logInfo(receivePack, "✅ AgentBuild CR is completed successfully");

        } catch (Exception e) {
            logError(receivePack, "❌ Exception occurred: %s", e.getMessage());
            for (ReceiveCommand command : commands) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "Exception occurred: "  + e.getMessage());
            }
        }
    }

    private void logInfo(ReceivePack receivePack, String msg, Object... objects) {
        var line = msg.formatted(objects);
        log.info(line);
        receivePack.sendMessage(line);
        try {
            receivePack.getMessageOutputStream().flush();
        } catch (IOException e) {
            log.warn("Failed to flush message output stream", e);
        }
    }

    private void logError(ReceivePack receivePack, String msg, Object... objects) {
        var line = msg.formatted(objects);
        log.error(line);
        receivePack.sendError(line);
        try {
            receivePack.getMessageOutputStream().flush();
        } catch (IOException e) {
            log.warn("Failed to flush message output stream", e);
        }
    }

}
