package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.gitops.server.spring.GitRequestContextUtil;
import ai.tuna.fusion.metadata.crd.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
        var agentDeployment = GitRequestContextUtil.getAgentDeployment().orElseThrow(()-> new ServiceNotEnabledException("AgentDeployment is not associated with this Git repository URL."));
        var agentCatalogue = GitRequestContextUtil.getAgentCatalogue().orElseThrow(()-> new ServiceNotEnabledException("AgentCatalogue is associated with this Git repository URL."));
        // TODO check permissions

        ReceivePack receivePack = new ReceivePack(db);
        receivePack.setPreReceiveHook((rp, commands) -> {
            handlePreReceiveHook(agentDeployment, agentCatalogue, req, receivePack, commands);
        });
        return receivePack;
    }


    private void handlePreReceiveHook(AgentDeployment agentDeployment, AgentCatalogue agentCatalogue, HttpServletRequest req, ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        try {
            var zipPath = PipelineUtils.createRepoZip(receivePack, commands, agentDeployment.getSpec().getGit().getWatchedBranchName());
            receivePack.sendMessage("📦 Snapshot for repository is created successfully: " + zipPath);

            var sha256 = PipelineUtils.getSHA256Checksum(zipPath);
            receivePack.sendMessage("🔢 SHA256 for snapshot: " + sha256);

            var archiveId = PipelineUtils.fissionArchiveUpload(zipPath);
            receivePack.sendMessage("⏫ Archive ID for snapshot: " + archiveId);

            var agentBuild = PipelineUtils.createAgentBuild(kubernetesClient, agentDeployment, archiveId, sha256);
            receivePack.sendMessage("💾 AgentBuild CR is created successfully: " + agentBuild.getMetadata().getName());

            var podInfo = PipelineUtils.waitForJobPod(kubernetesClient, agentBuild.getMetadata().getName(), agentBuild.getMetadata().getNamespace());
            receivePack.sendMessage("💪 Job pod is created successfully: " + podInfo.getPodName());

            receivePack.sendMessage("⚒️ Streaming build log:");
            try (var logWatch = PipelineUtils.streamPodLogs(kubernetesClient, podInfo.getPodName(), agentBuild.getMetadata().getNamespace())) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(logWatch.getOutput()));
                String line;
                while ((line = reader.readLine()) != null) {
                    receivePack.sendMessage(line);
                }
            }
            receivePack.sendMessage("✅ AgentBuild is completed successfully");

        } catch (Exception e) {
            log.error("Exception occurred: {}", e.getMessage(), e);
            for (ReceiveCommand command : commands) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "Exception occurred: "  + e.getMessage());
            }
        }
    }

}
