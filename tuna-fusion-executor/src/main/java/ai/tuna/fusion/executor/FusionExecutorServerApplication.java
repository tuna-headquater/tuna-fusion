package ai.tuna.fusion.executor;

import ai.tuna.fusion.common.ConfigurationUtils;
import ai.tuna.fusion.common.PropertiesLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author robinqu
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(value = ExecutorProperties.class)
public class FusionExecutorServerApplication {
    public static void main(String[] args) {
        boolean propertyLoggerEnabled = Boolean.parseBoolean(ConfigurationUtils.getStaticValue("executor.propertyLoggerEnabled", "false"));
        if (propertyLoggerEnabled) {
            SpringApplication springApplication = new SpringApplication(FusionExecutorServerApplication.class);
            springApplication.addListeners(new PropertiesLogger());
            springApplication.run(args);
        } else {
            SpringApplication.run(FusionExecutorServerApplication.class, args);
        }

    }
}
