package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.metadata.crd.AgentBuild;
import ai.tuna.fusion.metadata.crd.AgentBuildSpec;
import ai.tuna.fusion.metadata.crd.AgentBuildStatus;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
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
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author robinqu
 */
@Slf4j
public class PipelineUtils {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMdd-HHmmss");

    public static AgentBuild createAgentBuild(
            KubernetesClient kubernetesClient,
            AgentDeployment agentDeployment,
            String archiveId,
            String sha256Checksum) {
        AgentBuild agentBuild = new AgentBuild();
        AgentBuildSpec spec = new AgentBuildSpec();
        spec.setBuilderImage(agentDeployment.getSpec().getBuildRecipe().getBuilderImage());
        spec.setBuildScript(agentDeployment.getSpec().getBuildRecipe().getBuildScript());
        spec.setServiceAccountName(agentDeployment.getSpec().getBuildRecipe().getServiceAccountName());
        var srcPkg = new AgentBuildSpec.SourcePackageResource();
        srcPkg.setProvider(AgentBuildSpec.SourcePackageProvider.Fission);
        srcPkg.setResourceId(archiveId);
        srcPkg.setSha256Checksum(sha256Checksum);
        spec.setSourcePackageResource(srcPkg);
        agentBuild.getMetadata().setName("%s-build-%s".formatted(agentDeployment.getMetadata().getName(), Instant.now().getEpochSecond()));
        agentBuild.getMetadata().setNamespace(agentDeployment.getMetadata().getNamespace());
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
            final String namespace) {
        var agentBuildWithJob = kubernetesClient.resources(AgentBuild.class)
                .inNamespace(namespace)
                .withName(agentBuildName)
                .waitUntilCondition(agentBuild ->
                                agentBuild.getStatus()!=null &&
                                        agentBuild.getStatus().getJobPod()!=null &&
                        StringUtils.isNotBlank(agentBuild.getStatus().getJobPod().getPodName()
                        ), 1, TimeUnit.MINUTES);

        if (Objects.isNull(agentBuildWithJob)) {
            throw new IllegalStateException("PodInfo is not present on AgentBuild");
        }

        return agentBuildWithJob.getStatus().getJobPod();
    }

    public static LogWatch streamPodLogs(final KubernetesClient client, String podName, String namespace) {
        return client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .watchLog();
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

                while ((line = reader.readLine()) != null) {
                    // 假设归档ID在输出的某一行中包含"ArchiveID:"标识
                    if (line.contains("ArchiveID:")) {
                        archiveId.append(line.trim());
                        break;
                    }
                }

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

    public static String getSHA256Checksum(String zipFilePath) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        File file = new File(zipFilePath);

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Invalid file path: " + zipFilePath);
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
