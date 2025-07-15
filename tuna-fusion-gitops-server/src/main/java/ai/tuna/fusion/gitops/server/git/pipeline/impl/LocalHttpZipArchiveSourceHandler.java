package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.PipelineUtils;
import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
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
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author robinqu
 */
@Slf4j
public class LocalHttpZipArchiveSourceHandler implements SourceArchiveHandler {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");


    private final Path zipRepositoryRoot;

    public LocalHttpZipArchiveSourceHandler(Path zipRepositoryRoot) {
        this.zipRepositoryRoot = zipRepositoryRoot;
    }

    @Override
    public PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, String defaultBranch) throws IOException {
        Repository repo = receivePack.getRepository();
        String timestamp = DATE_FORMAT.format(new Date());
        String zipName = "repo-snapshot-" + timestamp + ".zip";
        File zipFile = zipRepositoryRoot.resolve(zipName).toFile();
        log.info("Creating Zip archive: {}", zipFile.getAbsolutePath());

        try (RevWalk revWalk = new RevWalk(repo); ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {

            var filteredCommands = commands.stream().filter(cmd -> cmd.getType() == ReceiveCommand.Type.UPDATE || cmd.getType() == ReceiveCommand.Type.CREATE).filter(cmd -> StringUtils.equals(cmd.getRefName(), defaultBranch)).filter(cmd -> !cmd.getNewId().equals(ObjectId.zeroId())).toList();

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
        }

        var sourceArchive = new PodFunctionBuildSpec.SourceArchive();
        var zipSource = new PodFunction.FilesystemZipSource();
        zipSource.setPath(zipFile.getAbsolutePath());
        try {
            var sha256 = PipelineUtils.getSha256Checksum(zipFile.getAbsolutePath());
            zipSource.setSha256Checksum(sha256);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        sourceArchive.setFilesystemZipSource(zipSource);
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
