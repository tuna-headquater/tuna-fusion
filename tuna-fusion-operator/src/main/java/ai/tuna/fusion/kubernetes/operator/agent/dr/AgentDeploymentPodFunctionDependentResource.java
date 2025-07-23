package ai.tuna.fusion.kubernetes.operator.agent.dr;

import ai.tuna.fusion.metadata.crd.AgentResourceUtils;
import ai.tuna.fusion.kubernetes.operator.agent.reconciler.AgentDeploymentReconciler;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentSpec;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionSpec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild.A2A_RUNTIME_FILENAME;
import static ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild.AGENT_CARD_FILENAME;

/**
 * @author robinqu
 */
@KubernetesDependent(informer = @Informer(labelSelector = AgentDeploymentReconciler.SELECTOR))
public class AgentDeploymentPodFunctionDependentResource extends CRUDKubernetesDependentResource<PodFunction, AgentDeployment> {

    public static class MatchingDriverCondition implements Condition<PodFunction, AgentDeployment> {
        @Override
        public boolean isMet(DependentResource<PodFunction, AgentDeployment> dependentResource, AgentDeployment primary, Context<AgentDeployment> context) {
            var agentEnvironment = AgentResourceUtils.getReferencedAgentEnvironment(context.getClient(), primary).orElseThrow();
            return agentEnvironment.getSpec().getDriver().getType() == AgentEnvironmentSpec.DriverType.PodPool;
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    protected PodFunction desired(AgentDeployment primary, Context<AgentDeployment> context) {
        var podFunction = new PodFunction();
        podFunction.getMetadata().setName(AgentResourceUtils.computeFunctionName(primary));
        podFunction.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        podFunction.getMetadata().getLabels().put(AgentDeploymentReconciler.SELECTOR, "true");
        var podFunctionSpec = new PodFunctionSpec();
        var agentEnvironment = AgentResourceUtils
                .getReferencedAgentEnvironment(context.getClient(), primary)
                .orElseThrow(()-> new IllegalStateException("Agent Environment not found"));
        podFunctionSpec.setFileAssets(Arrays.asList(
                renderAgentCardJson(agentEnvironment, primary),
                renderA2aRuntimeConfigJson(primary)
        ));
        podFunctionSpec.setPodPoolName(AgentResourceUtils.computePodPoolName(agentEnvironment));
        podFunctionSpec.setAppType(PodFunctionSpec.AppType.AgentApp);
        podFunctionSpec.setEntrypoint(primary.getSpec().getEntrypoint());
        podFunction.setSpec(podFunctionSpec);
        return podFunction;
    }

    @SneakyThrows
    private PodFunction.FileAsset renderAgentCardJson(AgentEnvironment agentEnvironment, AgentDeployment agentDeployment)  {
        var originalAgentCard = agentDeployment.getSpec().getAgentCard();
        var agentCard = originalAgentCard.toBuilder().url(AgentResourceUtils.agentExternalUrl(agentDeployment, agentEnvironment)).build();
        return PodFunction.FileAsset.builder()
                .executable(false)
                .targetDirectory(PodFunction.TargetDirectory.DEPLOY_ARCHIVE)
                .fileName(AGENT_CARD_FILENAME)
                .content(objectMapper.writeValueAsString(agentCard))
                .build();
    }

    @SneakyThrows
    private PodFunction.FileAsset renderA2aRuntimeConfigJson(AgentDeployment agentDeployment) {
        var a2a = agentDeployment.getSpec().getA2a();
        if (a2a.getQueueManager().getProvider() == AgentDeploymentSpec.A2ARuntime.QueueManagerProvider.Redis) {
            Optional.ofNullable(a2a.getQueueManager().getRedis())
                    .ifPresent(redis -> redis.setTaskRegistryKey("tuna.fusion.a2a.task.%s".formatted(agentDeployment.getMetadata().getName())));
        }
        if (a2a.getTaskStore().getProvider() != AgentDeploymentSpec.A2ARuntime.TaskStoreProvider.InMemory) {
            Optional.ofNullable(a2a.getTaskStore().getSql())
                    .ifPresent(sql -> sql.setTaskStoreTableName("tuna-fusion-%s-tasks".formatted(agentDeployment.getMetadata().getName())));
        }
        return PodFunction.FileAsset.builder()
                .targetDirectory(PodFunction.TargetDirectory.DEPLOY_ARCHIVE)
                .content(objectMapper.writeValueAsString(a2a))
                .fileName(A2A_RUNTIME_FILENAME)
                .executable(false)
                .build();
    }

}
