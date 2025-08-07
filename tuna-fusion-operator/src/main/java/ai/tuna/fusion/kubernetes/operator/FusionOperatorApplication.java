package ai.tuna.fusion.kubernetes.operator;

import ai.tuna.fusion.common.ConfigurationUtils;
import ai.tuna.fusion.common.PropertiesLogger;
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
        boolean propertyLoggerEnabled = Boolean.parseBoolean(ConfigurationUtils.getStaticValue("operator.propertyLoggerEnabled", "false"));
        if (propertyLoggerEnabled) {
            SpringApplication springApplication = new SpringApplication(FusionOperatorApplication.class);
            springApplication.addListeners(new PropertiesLogger());
            springApplication.run(args);
        } else {
            SpringApplication.run(FusionOperatorApplication.class, args);
        }
    }
}
