package ai.tuna.fusion.kubernetes.operator.agent.dr;

import ai.tuna.fusion.kubernetes.operator.agent.AgentResourceUtils;
import ai.tuna.fusion.kubernetes.operator.agent.reconciler.AgentDeploymentReconciler;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentSpec;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import lombok.SneakyThrows;
import org.apache.commons.text.StringSubstitutor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author robinqu
 */
@KubernetesDependent(informer = @Informer(labelSelector = AgentDeploymentReconciler.SELECTOR))
public class AgentDeploymentPodFunctionDependentResource extends CRUDKubernetesDependentResource<PodFunction, AgentDeployment> {

    public static final String BUILD_SCRIPT_PATH = "/workspace/build.sh";
    public static final String AGENT_CARD_JSON_PATH = "/workspace/agent_card.json";
    public static final String A2A_RUNTIME_JSON_PATH = "/workspace/a2a_runtime.json";
    public static final String INIT_CONTAINER_SCRIPT_TEMPLATE = "echo -e '${buildScript}' > ${buildScriptPath} && " +
            "echo -e '%{agentCardJson}' > ${agentCardJsonPath} && " +
            "echo -e '%{a2aRuntimeConfigJson}' > ${a2aRuntimeConfigPath}";

    public static class MatchingDriverCondition implements Condition<PodFunction, AgentDeployment> {
        @Override
        public boolean isMet(DependentResource<PodFunction, AgentDeployment> dependentResource, AgentDeployment primary, Context<AgentDeployment> context) {
            var agentEnvironment = AgentResourceUtils.getReferencedAgentEnvironment(context.getClient(), primary).orElseThrow();
            return agentEnvironment.getSpec().getDriver().getType() == AgentEnvironmentSpec.DriverType.PodPool;
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected PodFunction desired(AgentDeployment primary, Context<AgentDeployment> context) {
        var agentEnvironment = AgentResourceUtils.getReferencedAgentEnvironment(context.getClient(), primary).orElseThrow();

        var podFunction = new PodFunction();
        podFunction.getMetadata().setName(AgentResourceUtils.computeFunctionName(primary));
        podFunction.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        var podFunctionSpec = new PodFunctionSpec();
        podFunction.setSpec(podFunctionSpec);
//        podFunctionSpec.setRoutePrefix(AgentResourceUtils.routeUrl(primary));
        podFunctionSpec.setEntrypoint(primary.getSpec().getEntrypoint());
        podFunctionSpec.setInitCommands(renderInitContainerScript(primary, agentEnvironment));
        return podFunction;
    }

    private List<String> renderInitContainerScript(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment) {
        StringSubstitutor substitutor = new StringSubstitutor(Map.of(
                agentEnvironment.getSpec().getDriver().getPodPoolSpec().getBuildScript(),
                BUILD_SCRIPT_PATH,
                renderAgentCardJson(agentDeployment, agentEnvironment),
                AGENT_CARD_JSON_PATH,
                renderA2aRuntimeConfigJson(agentDeployment, agentEnvironment),
                A2A_RUNTIME_JSON_PATH
        ));
        return Arrays.asList("sh", "-c", substitutor.replace(INIT_CONTAINER_SCRIPT_TEMPLATE));
    }

    @SneakyThrows
    private String renderAgentCardJson(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment)  {
        var originalAgentCard = agentDeployment.getSpec().getAgentCard();
        var agentCard = originalAgentCard.toBuilder().url(AgentResourceUtils.agentExternalUrl(agentDeployment, agentEnvironment)).build();
        return objectMapper.writeValueAsString(agentCard);
    }

    @SneakyThrows
    private String renderA2aRuntimeConfigJson(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment) {
        var a2a = agentDeployment.getSpec().getA2a();
        if (a2a.getQueueManager().getProvider() == AgentDeploymentSpec.A2ARuntime.QueueManagerProvider.Redis) {
            Optional.ofNullable(a2a.getQueueManager().getRedis())
                    .ifPresent(redis -> redis.setTaskRegistryKey("tuna.fusion.a2a.task.%s".formatted(agentDeployment.getMetadata().getName())));
        }
        if (a2a.getTaskStore().getProvider() != AgentDeploymentSpec.A2ARuntime.TaskStoreProvider.InMemory) {
            Optional.ofNullable(a2a.getTaskStore().getSql())
                    .ifPresent(sql -> sql.setTaskStoreTableName("tuna-fusion-%s-tasks".formatted(agentDeployment.getMetadata().getName())));
        }
        return objectMapper.writeValueAsString(a2a);
    }
}
