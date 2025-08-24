package ai.tuna.fusion.gitops.server.git.test;

import ai.tuna.fusion.gitops.server.git.PipelineUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

/**
 * @author robinqu
 */
@Slf4j

public class PipelineUtilsTest {

    @Test
    @Disabled
    public void testCompressDirectory() throws IOException {
        var zipFile = Files.createTempFile("test", ".zip").toString();
        log.info("zipfile: {}", zipFile);
        PipelineUtils.compressDirectory(
                "/Users/robinqu/Workspace/github/tuna-headquater/tuna-fusion-agent-samples",
                zipFile,
                ".git"
        );
    }

}
