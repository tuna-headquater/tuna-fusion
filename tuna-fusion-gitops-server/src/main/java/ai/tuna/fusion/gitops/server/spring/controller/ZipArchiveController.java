package ai.tuna.fusion.gitops.server.spring.controller;

import ai.tuna.fusion.gitops.server.spring.property.GitOpsServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author robinqu
 */
@Controller
@ConditionalOnProperty(name = "gitops.source-archive-handler.type", havingValue = "ZipArchiveOnLocalHttpServer", matchIfMissing = false)
public class ZipArchiveController {

    private final GitOpsServerProperties gitOpsServerProperties;

    public ZipArchiveController(GitOpsServerProperties gitOpsServerProperties) {
        this.gitOpsServerProperties = gitOpsServerProperties;
    }

    @RequestMapping(path = "/source_archives/{fileId}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Resource> downloadZipFile(@PathVariable String fileId) throws IOException {
        // Assuming the file path is constructed based on fileId
        Path zipFilePath = computeZipFilePath(fileId);
        
        if (!Files.exists(zipFilePath)) {
            throw new FileNotFoundException("File not found: " + fileId);
        }

        Resource resource = new UrlResource(zipFilePath.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    private Path computeZipFilePath(String fileId) {
        return gitOpsServerProperties.getSourceArchiveHandler().getZipArchiveOnLocalHttpServer().getZipRepositoryRoot().resolve(fileId + ".zip");
    }


}
