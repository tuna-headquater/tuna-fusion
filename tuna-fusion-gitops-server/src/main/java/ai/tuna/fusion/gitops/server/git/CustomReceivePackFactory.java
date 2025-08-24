package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import ai.tuna.fusion.gitops.server.spring.GitRequestContextUtil;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.tuna.fusion.gitops.server.git.PipelineUtils.logError;
import static ai.tuna.fusion.gitops.server.git.PipelineUtils.logInfo;
import static ai.tuna.fusion.metadata.informer.impl.ResourceInformersWrapper.ALL_NAMESPACE_IDENTIFIER;

/**
 * @author robinqu
 */
@Slf4j
public class CustomReceivePackFactory implements ReceivePackFactory<HttpServletRequest>, PreReceiveHook, PostReceiveHook {

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
        // TODO check permissions
        ReceivePack receivePack = new ReceivePack(db);
        receivePack.setPreReceiveHook(this);
        receivePack.setPostReceiveHook(this);
        return receivePack;
    }

    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        try {
            logInfo(receivePack, "🪝 Post receive hook called at dir %s", receivePack.getRepository().getDirectory());
            //        var urlParams = GitRequestContextUtil.getGitURLParams().orElseThrow(()-> new ServiceNotEnabledException("Git URL params are not associated with this Git repository URL."));
            var agentDeployment = GitRequestContextUtil.getAgentDeployment().orElseThrow(()-> new ServiceNotEnabledException("AgentDeployment is not associated with this Git repository URL."));
//        var agentEnvironment = GitRequestContextUtil.getAgentEnvironment().orElseThrow(()-> new ServiceNotEnabledException("AgentEnvironment is associated with this Git repository URL."));
            var podFunction = GitRequestContextUtil.getPodFunction().orElseThrow(()-> new ServiceNotEnabledException("PodFunction is not associated with this Git repository URL."));


            var gitOptions = agentDeployment.getSpec().getGit();
            var sourceArchive = sourceArchiveHandler.createSourceArchive(receivePack, commands, gitOptions);
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

            logInfo(receivePack, "⏳ Waiting for final status of PodFunctionBuild with timeout of 3 mins");
            AtomicBoolean isSucceeded = new AtomicBoolean(false);
            var isCompleted = !kubernetesClient.resources(PodFunctionBuild.class)
                    .inNamespace(functionBuild.getMetadata().getNamespace())
                    .withName(functionBuild.getMetadata().getName())
                    .informOnCondition(podFunctionBuilds -> {
                        return Optional.ofNullable(CollectionUtils.firstElement(podFunctionBuilds))
                                .map(PodFunctionBuild::getStatus)
                                .map(PodFunctionBuildStatus::getPhase)
                                .map(phase -> {
                                    isSucceeded.set(phase == PodFunctionBuildStatus.Phase.Succeeded);
                                    return phase == PodFunctionBuildStatus.Phase.Succeeded || phase == PodFunctionBuildStatus.Phase.Failed;
                                })
                                .orElse(false);
                    }).get(3, TimeUnit.MINUTES).isEmpty();
            if (isCompleted && isSucceeded.get()) {
                logInfo(receivePack, "✅ FunctionBuild CR is completed successfully");
            } else {
                throw new RuntimeException("❌ FunctionBuild is in Failed Phase. Please check logs.");
            }
        } catch (Exception e) {
            logError(receivePack, e, "🪝 Exception occurred during PostReceiveHook: %s", e.getMessage());
            for (ReceiveCommand command : commands) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON,  e.getMessage());
            }
        }

    }

    @SneakyThrows
    @Override
    public void onPreReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        logInfo(receivePack, "🪝 PreReceive Hook called at dir %s", receivePack.getRepository().getDirectory());
        try {
            var agentEnvironment = GitRequestContextUtil.getAgentEnvironment().orElseThrow(()-> new ServiceNotEnabledException("AgentEnvironment is associated with this Git repository URL."));
            var podFunction = GitRequestContextUtil.getPodFunction().orElseThrow(()-> new ServiceNotEnabledException("PodFunction is not associated with this Git repository URL."));

            if (agentEnvironment.getSpec().getDriver().getType() != AgentEnvironmentSpec.DriverType.PodPool) {
                throw new ServiceNotEnabledException("❌ Only PodPool driver is supported now");
            }

            if (podFunction.getStatus().getCurrentBuild() != null) {
                throw new ServiceNotEnabledException("Current build is running for this AgentDeployment: " + podFunction.getStatus().getCurrentBuild().getName());
            }
        } catch (Exception e) {
            logError(receivePack, e, "🪝 Exception occurred during PreReceiveHook: %s", e.getMessage());
            for (ReceiveCommand command : commands) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, e.getMessage());
            }
        }
    }


}
