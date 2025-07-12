package ai.tuna.fusion.gitops.server.git;

import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
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
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author robinqu
 */
@Slf4j
public class PipelineUtils {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMdd-HHmmss");


    public static Optional<PodFunctionBuild> getAgentBuild(KubernetesClient client, String ns, String name) {
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
        spec.setSourceArchivePath(sourceArchivePath);
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
     * @param kubernetesClient
     * @param agentBuildName
     * @param namespace
     * @return
     */
    public static PodFunctionBuildStatus.JobPodInfo waitForJobPod(
            final KubernetesClient kubernetesClient,
            final String agentBuildName,
            final String namespace) throws InterruptedException {

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
     * 解压ZIP文件到目标目录
     * 符合以下规范：
     * 1. 路径安全校验（防止路径穿越攻击）
     * 2. 异常处理规范
     * 3. 资源管理规范
     * 4. 日志记录规范
     */
    public static void unzipArchive(String zipPath, String destDir) throws IOException {
        // 参数验证
        if (zipPath == null || destDir == null) {
            throw new IllegalArgumentException("zipPath and destDir must not be null");
        }

        File zipFile = new File(zipPath);
        File destinationDir = new File(destDir);

        // 文件存在性验证
        if (!zipFile.exists() || !zipFile.isFile()) {
            throw new IllegalArgumentException("Invalid zip file: " + zipPath);
        }

        // 创建目标目录（如果不存在）
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Failed to create destination directory: " + destDir);
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // 路径安全检查
                String entryName = entry.getName();
                if (isUnreasonablePath(entryName)) {
                    String errorMessage = String.format("Invalid path in zip archive: %s. Path must not contain absolute paths, Windows-style paths, or escape sequences.", entryName);
                    log.warn(errorMessage);
                    throw new IllegalArgumentException(errorMessage);
                }

                File destFile = new File(destinationDir, entryName);

                // 防止目录遍历攻击
                if (!destFile.getCanonicalPath().startsWith(destinationDir.getCanonicalPath() + File.separator)) {
                    String errorMessage = String.format("Invalid zip entry: %s. Potential path traversal attempt detected.", entryName);
                    log.warn(errorMessage);
                    throw new IllegalArgumentException(errorMessage);
                }

                // 如果是目录则创建
                if (entry.isDirectory()) {
                    if (!destFile.exists() && !destFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + destFile.getAbsolutePath());
                    }
                    continue;
                }

                // 创建父目录（如果需要）
                if (!destFile.getParentFile().exists() && !destFile.getParentFile().mkdirs()) {
                    throw new IOException("Failed to create parent directories for: " + destFile.getAbsolutePath());
                }

                // 写入文件内容
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = zis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            String errorMessage = String.format("Error unzipping archive from %s to %s. Error: %s", zipPath, destDir, e.getMessage());
            log.error(errorMessage, e);
            throw new IOException(errorMessage, e);
        }
    }

    private static boolean isUnreasonablePath(String path) {
        return path.startsWith("/") ||
                (System.getProperty("os.name").toLowerCase().contains("win") && path.matches("^[A-Za-z]:.*")) ||
                path.contains("..") ||
                path.contains(":");
    }

    public static void saveRepoToDirectory(Path destinationPath, ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch) throws IOException {

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
