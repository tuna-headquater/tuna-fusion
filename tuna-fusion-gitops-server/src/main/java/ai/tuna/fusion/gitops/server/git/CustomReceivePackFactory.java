package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import ai.tuna.fusion.gitops.server.spring.GitRequestContextUtil;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static ai.tuna.fusion.metadata.informer.impl.ResourceInformersWrapper.ALL_NAMESPACE_IDENTIFIER;

/**
 * @author robinqu
 */
@Slf4j
public class CustomReceivePackFactory implements ReceivePackFactory<HttpServletRequest> {

    private final KubernetesClient kubernetesClient;

    private final SourceArchiveHandler sourceArchiveHandler;

    private final Set<String> watchedNamespaces;

    public CustomReceivePackFactory(KubernetesClient kubernetesClient, SourceArchiveHandler sourceArchiveHandler, Set<String> watchedNamespaces) {
        this.kubernetesClient = kubernetesClient;
        this.sourceArchiveHandler = sourceArchiveHandler;
        this.watchedNamespaces = watchedNamespaces;
        Preconditions.checkState(
                (watchedNamespaces.size()==1 && watchedNamespaces.contains(ALL_NAMESPACE_IDENTIFIER)) || !watchedNamespaces.isEmpty(),
                "watchedNamespaces should contain at least one namespace or '*' for all namespaces."
        );
    }

    @Override
    public ReceivePack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException {
        GitRequestContextUtil.initializeRequestAttributes(kubernetesClient, req, this.watchedNamespaces);
        var agentDeployment = GitRequestContextUtil.getAgentDeployment().orElseThrow(()-> new ServiceNotEnabledException("AgentDeployment is not associated with this Git repository URL."));
        var agentEnvironment = GitRequestContextUtil.getAgentEnvironment().orElseThrow(()-> new ServiceNotEnabledException("AgentEnvironment is associated with this Git repository URL."));
        // TODO check permissions

        ReceivePack receivePack = new ReceivePack(db);
        receivePack.setPreReceiveHook((rp, commands) -> {
            handlePreReceiveHook(agentEnvironment, agentDeployment, req, receivePack, commands);
        });
        return receivePack;
    }

    private void handlePreReceiveHook(AgentEnvironment agentEnvironment, AgentDeployment agentDeployment, HttpServletRequest req, ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        if (agentEnvironment.getSpec().getDriver().getType() != AgentEnvironmentSpec.DriverType.PodPool) {
            logError(receivePack, null, "❌ Only PodPool driver is supported now");
            return;
        }

        try {
            var podFunction = getReferencedPodFunction(agentDeployment).orElseThrow(()-> new ServiceNotEnabledException("PodFunction for AgentDeployment is not found"));

            if (podFunction.getStatus().getCurrentBuild() != null) {
                throw new RuntimeException("Current build is running for this AgentDeployment: " + podFunction.getStatus().getCurrentBuild().getName());
            }

            var sourceArchive = sourceArchiveHandler.createSourceArchive(receivePack, commands, agentDeployment.getSpec().getGit().getWatchedBranchName());
            logInfo(receivePack, "📦 SourceArchive for repository is created successfully: %s", sourceArchive);

            var functionBuild = PipelineUtils.createAgentFunctionBuild(kubernetesClient, agentDeployment, podFunction, sourceArchive);
            logInfo(receivePack, "💾 FunctionBuild CR is created successfully: %s", functionBuild.getMetadata().getName());

            var podInfo = PipelineUtils.waitForJobPod(kubernetesClient, functionBuild.getMetadata().getName(), functionBuild.getMetadata().getNamespace());
            logInfo(receivePack, "⚒️ Job pod is created successfully: %s", podInfo.getPodName());

            PipelineUtils.streamPodLogs(kubernetesClient,
                    podInfo.getPodName(),
                    agentDeployment.getMetadata().getNamespace(),
                    line -> logInfo(receivePack, line)
            );

            logInfo(receivePack, "⏳ Waiting for final status of PodFunctionBuild with timeout of 5 mins");
            var isBuildSucceeded = !kubernetesClient.resources(PodFunctionBuild.class)
                    .inNamespace(functionBuild.getMetadata().getNamespace())
                    .withName(functionBuild.getMetadata().getName())
                    .informOnCondition(podFunctionBuilds -> {
                        return Optional.ofNullable(CollectionUtils.firstElement(podFunctionBuilds))
                                .map(PodFunctionBuild::getStatus)
                                .map(PodFunctionBuildStatus::getPhase)
                                .map(phase -> phase == PodFunctionBuildStatus.Phase.Succeeded)
                                .orElse(false);
                    }).get(3, TimeUnit.MINUTES).isEmpty();

            if (isBuildSucceeded) {
                logInfo(receivePack, "✅ FunctionBuild CR is completed successfully");
            } else {
                throw new RuntimeException("FunctionBuild is in Failed Phase. Please check logs.");
            }
        } catch (Exception e) {
            logError(receivePack, e, "❌ Exception occurred: %s", e.getMessage());
            for (ReceiveCommand command : commands) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "Exception occurred: "  + e.getMessage());
            }
        }
    }

    private void logInfo(ReceivePack receivePack, String msg, Object... objects) {
        var line = objects.length >0 ? msg.formatted(objects) : msg;
        log.info(line);
        receivePack.sendMessage(line);
        try {
            receivePack.getMessageOutputStream().flush();
        } catch (IOException e) {
            log.warn("Failed to flush message output stream", e);
        }
    }

    private void logError(ReceivePack receivePack, Exception ex, String msg, Object... objects) {
        var line = objects.length > 0 ? msg.formatted(objects) : msg;
        log.error(line, ex);
        receivePack.sendError(line);
        try {
            receivePack.getMessageOutputStream().flush();
        } catch (IOException e) {
            log.warn("Failed to flush message output stream", e);
        }
    }

    private Optional<PodFunction> getReferencedPodFunction(AgentDeployment agentDeployment) {
        return Optional.ofNullable(kubernetesClient.resources(PodFunction.class)
                .inNamespace(agentDeployment.getMetadata().getNamespace())
                .withName(agentDeployment.getStatus().getFunction().getFunctionName())
                .get());
    }

}
