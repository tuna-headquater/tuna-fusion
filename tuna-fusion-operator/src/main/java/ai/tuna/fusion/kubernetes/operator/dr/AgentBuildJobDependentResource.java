package ai.tuna.fusion.kubernetes.operator.dr;

import ai.tuna.fusion.kubernetes.operator.ResourceUtils;
import ai.tuna.fusion.metadata.crd.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;
import java.util.Optional;

import static ai.tuna.fusion.kubernetes.operator.reconciler.AgentBuildReconciler.SELECTOR;

/**
 * @author robinqu
 */
@KubernetesDependent(
        informer = @Informer(labelSelector = SELECTOR)
)
@Slf4j
public class AgentBuildJobDependentResource extends CRUDKubernetesDependentResource<Job, AgentBuild> {

    public static class IsJobRequiredCondition implements Condition<Job, AgentBuild> {
        @Override
        public boolean isMet(DependentResource<Job, AgentBuild> dependentResource, AgentBuild primary, Context<AgentBuild> context) {
            var phase = Optional.ofNullable(primary.getStatus())
                    .map(AgentBuildStatus::getPhase)
                    .orElse(AgentBuildStatus.Phase.Pending);
            return phase != AgentBuildStatus.Phase.Failed && phase != AgentBuildStatus.Phase.Succeeded;
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected Job desired(AgentBuild primary, Context<AgentBuild> context) {
//        log.debug("Creating Job DR for build {}", primary.getMetadata());
        var agentDeployment = ResourceUtils.getReferencedAgentDeployment(context.getClient(), primary).orElseThrow();
        var agentEnvironment = ResourceUtils.getReferencedAgentEnvironment(context.getClient(), agentDeployment).orElseThrow();
        var routeUrl = routeUrl(agentDeployment);
        var agentCardJson = renderAgentCardJson(agentDeployment, agentEnvironment);

        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels(SELECTOR, "true")
                .endMetadata()
                .withNewSpec()
                .withTtlSecondsAfterFinished(60*60)
                // disallow retry
                .withBackoffLimit(0)
                .withCompletions(1)
                .withActiveDeadlineSeconds(60 * 10L)
                .withNewTemplate()
                .withNewMetadata()
                .withNamespace(primary.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withServiceAccountName(primary.getSpec().getServiceAccountName())
                .withRestartPolicy("Never")
                .addNewInitContainer()
                .withName("builder-init-container")
                .withImage("busybox:latest")
                .withCommand("sh", "-c", "echo -e '%s' > /workspace/build.sh && chmod +x /workspace/build.sh && cat /workspace/build.sh && echo -e '%s' > /workspace/agent_card.json && cat /workspace/agent_card.json".formatted(
                        primary.getSpec().getBuildScript(),
                        agentCardJson
                ))
                .addNewVolumeMount()
                .withName("builder-script-volume")
                .withMountPath("/workspace")
                .endVolumeMount()
                .endInitContainer()
                .addNewContainer()
                .withName("build-container")
                .withImage(primary.getSpec().getBuilderImage())
                .withCommand("sh", "/workspace/build.sh")
                .addToEnv(
                        new EnvVar("AGENT_CARD_JSON_PATH", "/workspace/agent_card.json", null),
                        new EnvVar("FUNCTION_SOURCE_ARCHIVE_ID", primary.getSpec().getSourcePackageResource().getResourceId(), null),
                        new EnvVar("FUNCTION_NAME", ResourceUtils.computeFunctionName(agentDeployment), null),
                        new EnvVar("ROUTE_NAME", ResourceUtils.computeRouteName(agentDeployment), null),
                        new EnvVar("ROUTE_URL", routeUrl, null),
                        new EnvVar("FUNCTION_ENV", agentDeployment.getSpec().getEnvironmentName(), null),
                        new EnvVar("NAMESPACE", primary.getMetadata().getNamespace(), null),
                        new EnvVar("CATALOGUE_NAME", ResourceUtils.getReferenceAgentCatalogueName(agentDeployment), null)
                )
                .addNewVolumeMount()
                .withMountPath("/workspace")
                .withName("builder-script-volume")
                .endVolumeMount()
                .endContainer()
                .addNewVolume()
                .withName("builder-script-volume")
                .withNewEmptyDir()
                .endEmptyDir()
                .and()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
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
        if (agentEnvironment.getSpec().getEngineType() == AgentEnvironmentSpec.EngineType.Fission) {
            return "%s://%s/fission-function%s".formatted(
                    agentEnvironment.getSpec().getEndpoint().getProtocol(),
                    agentEnvironment.getSpec().getEndpoint().getHost(),
                    routeUrl(agentDeployment)
            );
        }
        throw new IllegalArgumentException("Unsupported engine type: " + agentEnvironment.getSpec().getEngineType());
    }

    @SneakyThrows
    private String renderAgentCardJson(AgentDeployment agentDeployment, AgentEnvironment agentEnvironment)  {
        var originalAgentCard = agentDeployment.getSpec().getAgentCard();
        var agentCard = originalAgentCard.toBuilder().url(agentUrl(agentDeployment, agentEnvironment)).build();
        return objectMapper.writeValueAsString(agentCard);
    }

    private String jobName(AgentBuild primary) {
        return String.format("build-%s", primary.getMetadata().getName());
    }
}
