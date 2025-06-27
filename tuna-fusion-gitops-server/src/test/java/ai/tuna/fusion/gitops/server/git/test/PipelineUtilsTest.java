package ai.tuna.fusion.gitops.server.git.test;

import ai.tuna.fusion.gitops.server.git.PipelineUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * @author robinqu
 */
@Slf4j
public class PipelineUtilsTest {

    @Test
    public void testSha256Checksum() throws NoSuchAlgorithmException, IOException {
        var checksum = PipelineUtils.getSha256Checksum("/Users/robinqu/Downloads/test.zip");
        log.info("checksum={}", checksum);
    }
}
