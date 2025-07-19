package ai.tuna.fusion;

import ai.tuna.fusion.intgrationtest.IntegrationTestProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author robinqu
 */
@SpringBootApplication
@EnableConfigurationProperties(IntegrationTestProperties.class)
public class IntegrationTestApplication {
}
