package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.PipelineUtils;
import ai.tuna.fusion.gitops.server.spring.property.GitOpsServerProperties;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author robinqu
 */
@Slf4j
public class LocalHttpZipArchiveSourceHandler extends BaseArchiveHandler {

    private final Path zipRepositoryRoot;
    private final String httpServerBaseUrl;

    public LocalHttpZipArchiveSourceHandler(GitOpsServerProperties.SourceArchiveHandlerProperties.ZipArchiveOnLocalHttpServerProperties properties) {
        this.zipRepositoryRoot = properties.getZipRepositoryRoot();
        this.httpServerBaseUrl = properties.getHttpServerBaseUrl();
    }

    private String getZipUrl(String fileId) {
        return httpServerBaseUrl + "/source_archives/" + URLEncoder.encode(fileId, StandardCharsets.UTF_8);
    }

    @Override
    public PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch) throws IOException {
        Repository repo = receivePack.getRepository();
        var fileId = UUID.randomUUID().toString();
        File zipFile = zipRepositoryRoot.resolve(fileId + ".zip").toFile();
        log.info("[createSourceArchive] Creating Zip archive: {}", zipFile.getAbsolutePath());

        try (RevWalk revWalk = new RevWalk(repo); ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            var tree = filterCommands(revWalk, defaultBranch, commands);
            // 创建新的TreeWalk并正确初始化
            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.setRecursive(true);
                // 必须添加树源再开始遍历
                treeWalk.addTree(tree);
                log.info("[createSourceArchive] Tree count after addTree: {}", treeWalk.getTreeCount());

                while (treeWalk.next()) {
                    addTreeEntryToZip(receivePack, treeWalk, zos);
                }
                log.debug("[createSourceArchive] Finished adding {} entries to zip", treeWalk.getPathString());
            }
        }

        var sourceArchive = new PodFunctionBuildSpec.SourceArchive();
        var zipSource = new PodFunction.HttpZipSource();
        zipSource.setUrl(getZipUrl(fileId));
        try {
            var sha256 = PipelineUtils.getSha256Checksum(zipFile.getAbsolutePath());
            zipSource.setSha256Checksum(sha256);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        sourceArchive.setHttpZipSource(zipSource);
        return sourceArchive;
    }

    private void addTreeEntryToZip(ReceivePack receivePack, TreeWalk treeWalk, ZipOutputStream zos) throws IOException {
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
