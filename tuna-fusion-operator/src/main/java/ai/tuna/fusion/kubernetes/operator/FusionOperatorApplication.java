package ai.tuna.fusion.kubernetes.operator;

import ai.tuna.fusion.kubernetes.operator.config.OperatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author robinqu
 */
@SpringBootApplication
@EnableConfigurationProperties(OperatorProperties.class)
public class FusionOperatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FusionOperatorApplication.class, args);
    }
}
