package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.metadata.crd.*;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author robinqu
 */
@Slf4j
public class PipelineUtils {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMdd-HHmmss");


    public static Optional<AgentBuild> getAgentBuild(KubernetesClient client, String ns, String name) {
        return Optional.ofNullable(client.resources(AgentBuild.class)
                .inNamespace(ns)
                .withName(name)
                .get());
    }

    public static AgentBuild createAgentBuild(
            KubernetesClient kubernetesClient,
            AgentDeployment agentDeployment,
            AgentEnvironment agentEnvironment,
            String archiveId,
            String sha256Checksum) {
        AgentBuild agentBuild = new AgentBuild();
        AgentBuildSpec spec = new AgentBuildSpec();
        spec.setBuilderImage(agentEnvironment.getSpec().getBuildRecipe().getBuilderImage());
        spec.setBuildScript(agentEnvironment.getSpec().getBuildRecipe().getBuildScript());
        spec.setServiceAccountName(agentEnvironment.getSpec().getBuildRecipe().getServiceAccountName());
        var srcPkg = new AgentBuildSpec.SourcePackageResource();
        srcPkg.setProvider(AgentBuildSpec.SourcePackageProvider.Fission);
        srcPkg.setResourceId(archiveId);
        srcPkg.setSha256Checksum(sha256Checksum);
        spec.setSourcePackageResource(srcPkg);
        agentBuild.setSpec(spec);

        agentBuild.getMetadata().setName("%s-build-%s".formatted(agentDeployment.getMetadata().getName(), Instant.now().getEpochSecond()));
        agentBuild.getMetadata().setNamespace(agentDeployment.getMetadata().getNamespace());
        OwnerReference ownerReference = new OwnerReference(
                HasMetadata.getApiVersion(AgentDeployment.class),
                false,
                true,
                HasMetadata.getKind(AgentDeployment.class),
                agentDeployment.getMetadata().getName(),
                agentDeployment.getMetadata().getUid()
        );
        agentBuild.getMetadata().getOwnerReferences().add(ownerReference);
        return kubernetesClient.resource(agentBuild)
                .inNamespace(agentDeployment.getMetadata().getNamespace())
                .create();
    }


    /**
     * Watch for events from AgentBuild. Return pod name in AgentBuildStatus if it's present and valid.
     * @param kubernetesClient
     * @param agentBuildName
     * @param namespace
     * @return
     */
    public static AgentBuildStatus.JobPodInfo waitForJobPod(
            final KubernetesClient kubernetesClient,
            final String agentBuildName,
            final String namespace) throws InterruptedException {

        var agentBuildWithJob = kubernetesClient.resources(AgentBuild.class)
                .inNamespace(namespace)
                .withName(agentBuildName)
                .waitUntilCondition(agentBuild -> {
                    var podInfo = Optional.ofNullable(agentBuild)
                            .map(AgentBuild::getStatus)
                            .map(AgentBuildStatus::getJobPod);
                    var valid = podInfo.map(AgentBuildStatus.JobPodInfo::getPodName).map(StringUtils::isNotBlank).orElse(false) &&
                            podInfo.map(p -> !StringUtils.equals(p.getPodPhase(), "Pending")).orElse(false);
                    log.debug("Check pod readiness: AgentBuild={}/{}, podInfo={}, valid={}", namespace, agentBuildName, podInfo.orElse(null), valid);
                    return valid;
                }, 5, TimeUnit.MINUTES);
        return Optional.ofNullable(agentBuildWithJob).map(AgentBuild::getStatus).map(AgentBuildStatus::getJobPod).orElseThrow();

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

    /**
     * Execute `fission` CLI to upload archive and parse archive ID from stdout
     * @param zipPath The path to the zip file
     * @return The archive ID parsed from process stdout
     */
    public static String fissionArchiveUpload(String zipPath) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("fission", "archive", "upload", "--name", zipPath);
            Process process = processBuilder.start();
            try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()));
            ) {

                String line;
                StringBuilder archiveId = new StringBuilder();

                StringBuilder stdout = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    stdout.append(line);
                    // 假设归档ID在输出的某一行中包含"ArchiveID:"标识
                    if (line.contains("ID:")) {
                        archiveId.append(line.trim());
                        break;
                    }
                }
                log.debug("fission archive command stdout: {}", stdout);

                // 读取并记录错误流（可选）
                while ((line = errorReader.readLine()) != null) {
                    log.warn("Fission command error output: {}", line);
                }

                int exitCode = process.waitFor();
                if (exitCode != 0 || archiveId.isEmpty()) {
                    throw new RuntimeException("Failed to execute fission archive upload, exit code: " + exitCode);
                }

                // 提取实际的Archive ID值
                return archiveId.toString().split(":")[1].trim();
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

    public static String createRepoZip(ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch) throws IOException {
        Repository repo = receivePack.getRepository();
        String timestamp = DATE_FORMAT.format(new Date());
        String zipName = "repo-snapshot-" + timestamp + ".zip";
        File zipFile = new File(repo.getDirectory().getParentFile(), zipName);
        log.info("Creating Zip archive: {}", zipFile.getAbsolutePath());

        try (RevWalk revWalk = new RevWalk(repo);
             ZipOutputStream zos = new ZipOutputStream(
                     new BufferedOutputStream(new FileOutputStream(zipFile)))) {

            var filteredCommands = commands.stream()
                    .filter(cmd -> cmd.getType() == ReceiveCommand.Type.UPDATE || cmd.getType() == ReceiveCommand.Type.CREATE)
                    .filter(cmd -> StringUtils.equals(cmd.getRefName(), defaultBranch))
                    .filter(cmd -> !cmd.getNewId().equals(ObjectId.zeroId()))
                    .toList();

            if (filteredCommands.isEmpty()) {
                throw new IllegalStateException("No valid commands for default branch found");
            }
            log.info("{} commands selected for repo {}", filteredCommands.size(), repo.getDirectory());

            // 只使用第一个有效命令的提交
            ReceiveCommand cmd = filteredCommands.getFirst();
            ObjectId commitId = cmd.getNewId();
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            log.debug("Using tree from commit: {}", commitId.getName());

            // 创建新的TreeWalk并正确初始化
            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.setRecursive(true);
                // 必须添加树源再开始遍历
                treeWalk.addTree(tree);
                log.info("Tree count after addTree: {}", treeWalk.getTreeCount());

                while (treeWalk.next()) {
                    addTreeEntryToZip(receivePack, treeWalk, zos);
                }
                log.debug("Finished adding {} entries to zip", treeWalk.getPathString());
            }
        } catch (Exception e) {
            log.error("Error creating zip archive", e);
            throw e;
        }
        return zipFile.getAbsolutePath();
    }

    private static void addTreeEntryToZip(ReceivePack receivePack,
                                   TreeWalk treeWalk,
                                   ZipOutputStream zos)
            throws IOException {

        String path = treeWalk.getPathString();
        if (path.startsWith(".git")) {
            return;
        }
        receivePack.sendMessage("Adding zip entry: " + path);
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);

        ObjectId objectId = treeWalk.getObjectId(0);
        try (InputStream in = receivePack.getRepository().open(objectId).openStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }

        zos.closeEntry();
    }
}
