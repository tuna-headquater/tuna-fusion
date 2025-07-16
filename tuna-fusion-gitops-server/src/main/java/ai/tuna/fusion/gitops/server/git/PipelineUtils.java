package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author robinqu
 */
@Slf4j
public class PipelineUtils {
    public static Optional<PodFunctionBuild> getFunctionBuild(KubernetesClient client, String ns, String name) {
        return Optional.ofNullable(client.resources(PodFunctionBuild.class)
                .inNamespace(ns)
                .withName(name)
                .get());
    }

    public static PodFunctionBuild createAgentFunctionBuild(
            KubernetesClient kubernetesClient,
            AgentDeployment agentDeployment,
            PodFunction podFunction,
            PodFunctionBuildSpec.SourceArchive sourceArchive) {
        PodFunctionBuild podFunctionBuild = new PodFunctionBuild();
        PodFunctionBuildSpec spec = new PodFunctionBuildSpec();
        spec.setSourceArchive(sourceArchive);
        spec.setPodFunctionName(podFunction.getMetadata().getName());
        podFunctionBuild.setSpec(spec);
        podFunctionBuild.getMetadata().setName("%s-build-%s".formatted(agentDeployment.getMetadata().getName(), Instant.now().getEpochSecond()));
        podFunctionBuild.getMetadata().setNamespace(agentDeployment.getMetadata().getNamespace());
        OwnerReference ownerReference = new OwnerReference(
                HasMetadata.getApiVersion(PodFunction.class),
                false,
                true,
                HasMetadata.getKind(PodFunction.class),
                podFunction.getMetadata().getName(),
                podFunction.getMetadata().getUid()
        );
        podFunctionBuild.getMetadata().getOwnerReferences().add(ownerReference);
        return kubernetesClient.resource(podFunctionBuild)
                .inNamespace(agentDeployment.getMetadata().getNamespace())
                .create();
    }


    /**
     * Watch for events from AgentBuild. Return pod name in AgentBuildStatus if it's present and valid.
     */
    public static PodFunctionBuildStatus.JobPodInfo waitForJobPod(
            final KubernetesClient kubernetesClient,
            final String agentBuildName,
            final String namespace) {

        var agentBuildWithJob = kubernetesClient.resources(PodFunctionBuild.class)
                .inNamespace(namespace)
                .withName(agentBuildName)
                .waitUntilCondition(agentBuild -> {
                    var podInfo = Optional.ofNullable(agentBuild)
                            .map(PodFunctionBuild::getStatus)
                            .map(PodFunctionBuildStatus::getJobPod);
                    var valid = podInfo.map(PodFunctionBuildStatus.JobPodInfo::getPodName).map(StringUtils::isNotBlank).orElse(false) &&
                            podInfo.map(p -> !StringUtils.equals(p.getPodPhase(), "Pending")).orElse(false);
                    log.debug("Check pod readiness: AgentBuild={}/{}, podInfo={}, valid={}", namespace, agentBuildName, podInfo.orElse(null), valid);
                    return valid;
                }, 5, TimeUnit.MINUTES);
        return Optional.ofNullable(agentBuildWithJob).map(PodFunctionBuild::getStatus).map(PodFunctionBuildStatus::getJobPod).orElseThrow();

    }

    public static void streamPodLogs(final KubernetesClient client, String podName, String namespace, Consumer<String> logLineConsumer) throws IOException {
        try(var logWatch = client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .watchLog();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(logWatch.getOutput()))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                logLineConsumer.accept(line);
            }
        }
    }

    public static String getSha256Checksum(String filePath) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Invalid file path: " + filePath);
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();

        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

}
