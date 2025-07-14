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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author robinqu
 */
@Slf4j
public class PipelineUtils {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMdd-HHmmss");


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
            String sourceArchivePath) {
        PodFunctionBuild podFunctionBuild = new PodFunctionBuild();
        PodFunctionBuildSpec spec = new PodFunctionBuildSpec();
        spec.setSourceArchiveSubPath(sourceArchivePath);
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


    /**
     * Create snapshot for repository contained by `receivePack` with updates from `commands`. `defaultBranch` limits the branches to be considered.
     */
    public static void saveRepoToDirectory(Path destinationPath, ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch) throws IOException {
        Repository repo = receivePack.getRepository();
        log.info("Creating repository snapshot at: {}", destinationPath.toString());

        try (RevWalk revWalk = new RevWalk(repo)) {
            var filteredCommands = commands.stream()
                    .filter(cmd -> cmd.getType() == ReceiveCommand.Type.UPDATE || cmd.getType() == ReceiveCommand.Type.CREATE)
                    .filter(cmd -> StringUtils.equals(cmd.getRefName(), defaultBranch))
                    .filter(cmd -> !cmd.getNewId().equals(ObjectId.zeroId()))
                    .toList();

            if (filteredCommands.isEmpty()) {
                throw new IllegalStateException("No valid commands for default branch found");
            }
            log.info("{} commands selected for repo {}", filteredCommands.size(), repo.getDirectory());

            // Use the first valid command's commit
            ReceiveCommand cmd = filteredCommands.getFirst();
            ObjectId commitId = cmd.getNewId();
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            log.debug("Using tree from commit: {}", commitId.getName());

            // Create a new TreeWalk to traverse the repository tree
            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.setRecursive(true);
                treeWalk.addTree(tree);

                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (path.startsWith(".git")) {
                        continue; // Skip .git directory
                    }

                    receivePack.sendMessage("Processing: " + path);
                    ObjectId objectId = treeWalk.getObjectId(0);
                    try (InputStream in = repo.open(objectId).openStream()) {
                        // Create target file path
                        Path targetFile = destinationPath.resolve(path);
                        // Ensure parent directories exist
                        if (targetFile.getParent() != null && !Files.exists(targetFile.getParent())) {
                            Files.createDirectories(targetFile.getParent());
                        }
                        // Write file content
                        Files.copy(in, targetFile);
                    }
                }
                log.debug("Finished processing {} entries", treeWalk.getPathString());
            }
        } catch (Exception e) {
            log.error("Error creating repository snapshot", e);
            throw e;
        }
    }


}
