package ai.tuna.fusion.intgrationtest;

import ai.tuna.fusion.metadata.crd.AgentResourceUtils;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.podpool.*;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * @author robinqu
 */
@Slf4j
public record TestResourceContext(KubernetesClient client, String targetNamespace) {

    public <Resource extends HasMetadata> Resource loadYamlResource(Class<Resource> clazz, String classpath) {
        Resource resource = null;
        try (var is = getClass().getClassLoader().getResourceAsStream(classpath)) {
            resource = Serialization.unmarshal(is, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return client
                .resource(resource)
                .inNamespace(targetNamespace)
                .create();
    }

    public void awaitFor(Callable<Boolean> condition) {
        await().pollInterval(1, TimeUnit.SECONDS)
                .pollDelay(20, TimeUnit.MILLISECONDS)
                .atMost(60 * 2, TimeUnit.SECONDS)
                .until(condition);
    }

    private boolean checkCondition(PodPool podPool) {
        var deployName = PodPoolResourceUtils.computePodPoolDeploymentName(podPool);
        var svcName = PodPoolResourceUtils.computePodPoolServiceName(podPool);
        if (deployName.isEmpty() || svcName.isEmpty()) {
            return false;
        }
        var deploy = client.resources(Deployment.class)
                .inNamespace(targetNamespace)
                .withName(deployName)
                .get();
        var svc = client.resources(Service.class)
                .inNamespace(targetNamespace)
                .withName(svcName)
                .get();
        if (Objects.nonNull(deploy)) {
            log.info("deploy.status={}", deploy.getStatus());
        }
        return deploy != null && svc != null && Optional.ofNullable(deploy.getStatus()).map(DeploymentStatus::getAvailableReplicas).orElse(0).equals(podPool.getSpec().getPoolSize());
    }

    private boolean checkCondition(PodFunction podFunction) {
        return ResourceUtils.getKubernetesResource(client, podFunction.getMetadata().getName(), podFunction.getMetadata().getNamespace(), PodFunction.class).isPresent();
    }

    private boolean checkCondition(PodFunctionBuild podFunctionBuild) {
        var build = ResourceUtils.getKubernetesResource(client, podFunctionBuild.getMetadata().getName(), podFunctionBuild.getMetadata().getNamespace(), PodFunctionBuild.class).orElseThrow();
        return Optional.ofNullable(build.getStatus())
                .map(PodFunctionBuildStatus::getPhase)
                .map(phase -> {
                    if (phase == PodFunctionBuildStatus.Phase.Succeeded) {
                        return true;
                    }
                    if (phase == PodFunctionBuildStatus.Phase.Failed) {
                        log.info("PodFunctionBuildStatus failed with logs:\n {}", build.getStatus().getJobPod().getLogs());
                    }
                    return false;
                })
                .orElse(false);
    }

    private boolean checkCondition(AgentEnvironment agentEnvironment) {
        var podPoolName = AgentResourceUtils.computePodPoolName(agentEnvironment);
        return ResourceUtils.getKubernetesResource(client, podPoolName, agentEnvironment.getMetadata().getNamespace(), PodPool.class).map(this::checkCondition).orElse(false);
    }

    private boolean checkCondition(AgentDeployment agentDeployment) {
        var fnName = AgentResourceUtils.computeFunctionName(agentDeployment);
        return ResourceUtils
                .getKubernetesResource(client, fnName, agentDeployment.getMetadata().getNamespace(), PodFunction.class)
                .map(this::checkCondition)
                .orElse(false);
    }

    private boolean checkCondition(ConfigMap configMap) {
        return ResourceUtils.getKubernetesResource(client, configMap.getMetadata().getName(), configMap.getMetadata().getNamespace(), ConfigMap.class).isPresent();
    }

    private boolean checkCondition(Secret secret) {
        return ResourceUtils.getKubernetesResource(client, secret.getMetadata().getName(), secret.getMetadata().getNamespace(), Secret.class).isPresent();
    }

    public void awaitYamlResource(String type, String classpath) {
        awaitYamlResource(ResourceTreeNode.resolveType(type), classpath);
    }

    public <Resource extends HasMetadata> void awaitYamlResource(Class<Resource> clazz, String classpath) {
        var resource = loadYamlResource(clazz, classpath);
        log.info("Check condition for loaded resource: {}", ResourceUtils.computeResourceMetaKey(resource));
        switch (clazz.getSimpleName()) {
            case "AgentEnvironment":
                awaitFor(() -> checkCondition((AgentEnvironment) resource));
                break;
            case "AgentDeployment":
                awaitFor(() -> checkCondition((AgentDeployment) resource));
                break;
            case "PodPool":
                awaitFor(() -> checkCondition((PodPool) resource));
                break;
            case "PodFunction":
                awaitFor(() -> checkCondition((PodFunction) resource));
                break;
            case "PodFunctionBuild":
                awaitFor(() -> checkCondition((PodFunctionBuild) resource));
                break;
            case "Secret":
                awaitFor(() -> checkCondition((Secret) resource));
                break;
            case "ConfigMap":
                awaitFor(() -> checkCondition((ConfigMap) resource));
                break;
            case null, default:
                throw new IllegalArgumentException("Unsupported resource type: " + clazz);
        }
    }

    public void awaitResourceGroup(ResourceTreeNode resourceTreeNode) {
        var deps = resourceTreeNode.getDependencies();
        if (!CollectionUtils.isEmpty(deps)) {
            for (var dep : deps) {
                awaitResourceGroup(dep);
            }
        }
        awaitYamlResource(resourceTreeNode.getType(), resourceTreeNode.getClasspath());
    }

}
