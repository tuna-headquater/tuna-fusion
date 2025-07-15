package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.metadata.crd.AgentResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentSpec;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import lombok.SneakyThrows;

import java.util.Map;
import java.util.Optional;

/**
 * @author robinqu
 */
public class AgentFunctionBuildPodInitScript extends FunctionBuildPodInitScript {
    private final AgentDeployment agentDeployment;
    private final AgentEnvironment agentEnvironment;

    public AgentFunctionBuildPodInitScript(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment, PodFunctionBuildSpec.SourceArchive sourceArchive) {
        super(sourceArchive);
        this.agentDeployment = agentDeployment;
        this.agentEnvironment = agentEnvironment;
    }

    @Override
    void configureFileAssets(Map<String, FileAsset> fileAssets) {
        super.configureFileAssets(fileAssets);
        fileAssets.put(PodFunctionBuild.A2A_RUNTIME_FILENAME, renderA2aRuntimeConfigJson());
        fileAssets.put(PodFunctionBuild.AGENT_CARD_FILENAME, renderAgentCardJson());
    }

    @SneakyThrows
    private FileAsset renderAgentCardJson()  {
        var originalAgentCard = agentDeployment.getSpec().getAgentCard();
        var agentCard = originalAgentCard.toBuilder().url(AgentResourceUtils.agentExternalUrl(agentDeployment, agentEnvironment)).build();
        return FileAsset.builder()
                .executable(false)
                .content(getObjectMapper().writeValueAsString(agentCard))
                .build();
    }

    @SneakyThrows
    private FileAsset renderA2aRuntimeConfigJson() {
        var a2a = agentDeployment.getSpec().getA2a();
        if (a2a.getQueueManager().getProvider() == AgentDeploymentSpec.A2ARuntime.QueueManagerProvider.Redis) {
            Optional.ofNullable(a2a.getQueueManager().getRedis())
                    .ifPresent(redis -> redis.setTaskRegistryKey("tuna.fusion.a2a.task.%s".formatted(agentDeployment.getMetadata().getName())));
        }
        if (a2a.getTaskStore().getProvider() != AgentDeploymentSpec.A2ARuntime.TaskStoreProvider.InMemory) {
            Optional.ofNullable(a2a.getTaskStore().getSql())
                    .ifPresent(sql -> sql.setTaskStoreTableName("tuna-fusion-%s-tasks".formatted(agentDeployment.getMetadata().getName())));
        }
        return FileAsset.builder()
                .content(getObjectMapper().writeValueAsString(a2a))
                .executable(false)
                .build();
    }
}
