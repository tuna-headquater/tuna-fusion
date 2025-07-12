package ai.tuna.fusion.kubernetes.operator.agent.dr;

import ai.tuna.fusion.kubernetes.operator.agent.ResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentSpec;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironmentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodPool;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
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
            var agentEnvironment = ResourceUtils.getReferencedAgentEnvironment(context.getClient(), primary).orElseThrow();
            return agentEnvironment.getSpec().getDriver().getType() == AgentEnvironmentSpec.DriverType.PodPool;
        }
    }


    @Override
    protected PodFunction desired(AgentDeployment primary, Context<AgentDeployment> context) {
        return super.desired(primary, context);
    }


    private List<String> renderInitContainerScript(PodFunction podFunction, PodPool podPool) {
        StringSubstitutor substitutor = new StringSubstitutor(Map.of(
                podFunction.getSpec().getBuildScript(),
                BUILD_SCRIPT_PATH,
                renderAgentCardJson(agentDeployment, agentEnvironment),
                AGENT_CARD_JSON_PATH,
                renderA2aRuntimeConfigJson(agentDeployment, agentEnvironment),
                A2A_RUNTIME_JSON_PATH
        ));

        return Arrays.asList("sh", "-c", substitutor.replace(INIT_CONTAINER_SCRIPT_TEMPLATE));
    }

    private String routeUrl(AgentDeployment agentDeployment) {
        var substitutor = new StringSubstitutor(Map.of(
                "namespace", agentDeployment.getMetadata().getNamespace(),
                "agentCatalogueName", ResourceUtils.getReferenceAgentCatalogueName(agentDeployment),
                "agentDeploymentName", agentDeployment.getMetadata().getName(),
                "agentEnvironmentName", agentDeployment.getSpec().getEnvironmentName()
        ));
        var urlTemplate = agentDeployment.getSpec().getAgentCard().getUrl();
        if (!urlTemplate.startsWith("/")) {
            urlTemplate = "/" + urlTemplate;
        }
        return substitutor.replace(urlTemplate);
    }

    private String agentUrl(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment) {
        return "";
    }

    @SneakyThrows
    private String renderAgentCardJson(PodFunction podFunction, PodPool podPool)  {
        var originalAgentCard = agentDeployment.getSpec().getAgentCard();
        var agentCard = originalAgentCard.toBuilder().url(agentUrl(agentDeployment, agentEnvironment)).build();
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
