package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.gitops.server.git.PipelineUtils;
import ai.tuna.fusion.gitops.server.spring.property.GitOpsServerProperties;
import ai.tuna.fusion.metadata.crd.agent.AgentDeploymentSpec;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

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
    public PodFunctionBuildSpec.SourceArchive createSourceArchive(ReceivePack receivePack, Collection<ReceiveCommand> commands, AgentDeploymentSpec.GitOptions gitOptions) throws IOException {
        Repository repo = receivePack.getRepository();
        var fileId = UUID.randomUUID().toString();
        Path zipFile = zipRepositoryRoot.resolve(fileId + ".zip");
        var sourceArchive = new PodFunctionBuildSpec.SourceArchive();
        var zipSource = new PodFunction.HttpZipSource();
        zipSource.setUrl(getZipUrl(fileId));
        sourceArchive.setHttpZipSource(zipSource);
        log.info("[createSourceArchive] gitOptions={}, zipFile={}, repo.workTree={}", gitOptions, zipFile.toAbsolutePath(), repo.getDirectory());
        var destinationPath = Files.createTempDirectory(UUID.randomUUID().toString());
        var subPath = gitOptions.getSubPath();

        try {
            log.info("[createSourceArchive] createLocalSnapshotFolder: {}", destinationPath);
            createLocalSnapshotFolder(receivePack, commands, gitOptions, destinationPath);

            log.info("[createSourceArchive] Create zip archive and compute checksum: {}", zipFile);
            var sourcePath = destinationPath;
            if (StringUtils.isNoneBlank(subPath)) {
                sourcePath = destinationPath.resolve(subPath);
                if (!Files.exists(sourcePath)) {
                    log.info("[createSourceArchive] Illegal subPath: {}", sourcePath);
                }
            }
            PipelineUtils.compressGitDirectory(sourcePath, zipFile);
            var sha256 = PipelineUtils.getSha256Checksum(zipFile.toString());
            zipSource.setSha256Checksum(sha256);

            // return results
            return sourceArchive;
        } catch (Exception e) {
            throw new IOException("Failed to checkout and update submodules", e);
        } finally {
            FileUtils.deleteDirectory(destinationPath.toFile());
        }
    }



}
