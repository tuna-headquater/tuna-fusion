package ai.tuna.fusion.intgrationtest;

import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.*;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Getter;
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
public class TestResourceContext {

    @Getter
    private final KubernetesClient client;

    @Getter
    private final String targetNamespace;

    public TestResourceContext(KubernetesClient client, String targetNamespace) {
        this.client = client;
        this.targetNamespace = targetNamespace;
    }

    public <Resource extends HasMetadata> Resource loadYamlResource(Class<Resource> clazz, String classpath) {
        Resource resource = null;
        try(var is = getClass().getClassLoader().getResourceAsStream(classpath);) {
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

    public boolean checkCondition(PodPool podPool) {
        log.info("checkCondition for {}", ResourceUtils.computeResourceMetaKey(podPool));
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
        return deploy != null && svc != null && Optional.ofNullable(deploy.getStatus()).map(DeploymentStatus::getAvailableReplicas).orElse(0) == podPool.getSpec().getPoolSize();
    }

    public boolean checkCondition(PodFunction podFunction) {
        log.info("checkCondition for {}", ResourceUtils.computeResourceMetaKey(podFunction));
        return true;
    }

    public boolean checkCondition(PodFunctionBuild podFunctionBuild) {
        log.info("checkCondition for {}", ResourceUtils.computeResourceMetaKey(podFunctionBuild));
        var build = ResourceUtils.getKubernetesResource(client, podFunctionBuild.getMetadata().getName(), podFunctionBuild.getMetadata().getNamespace(), PodFunctionBuild.class).orElseThrow();
        return Optional.ofNullable(build.getStatus())
                .map(PodFunctionBuildStatus::getPhase)
                .map(phase -> phase == PodFunctionBuildStatus.Phase.Succeeded || phase == PodFunctionBuildStatus.Phase.Failed)
                .orElse(false);
    }

    public void awaitYamlResource(String type, String classpath) {
        awaitYamlResource(ResourceTreeNode.resolveType(type), classpath);
    }

    public <Resource extends HasMetadata> void awaitYamlResource(Class<Resource> clazz, String classpath) {
        var resource = loadYamlResource(clazz, classpath);
        log.info("Resource loaded: {}", resource);
        switch (clazz.getSimpleName()) {
            case "PodPool":
                awaitFor(()-> checkCondition((PodPool) resource));
                break;
            case "PodFunction":
                awaitFor(()-> checkCondition((PodFunction) resource));
                break;
            case "PodFunctionBuild":
                awaitFor(()-> checkCondition((PodFunctionBuild) resource));
                break;
            case null, default:
                throw new IllegalArgumentException("Unsupported resource type: " + clazz);
        }
    }

    public void awaitResourceGroup(ResourceTreeNode resourceTreeNode) {
        var deps = resourceTreeNode.getDependencies();
        if (!CollectionUtils.isEmpty(deps)) {
            for(var dep: deps) {
                awaitResourceGroup(dep);
            }
        }
        awaitYamlResource(resourceTreeNode.getType(), resourceTreeNode.getClasspath());
    }

}
