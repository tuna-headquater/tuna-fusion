package ai.tuna.fusion.gitops.server;

import ai.tuna.fusion.common.ConfigurationUtils;
import ai.tuna.fusion.common.PropertiesLogger;
import ai.tuna.fusion.gitops.server.spring.property.GitOpsServerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author robinqu
 */
@SpringBootApplication
@EnableConfigurationProperties(GitOpsServerProperties.class)
public class FusionGitOpsServerApplication {


    public static void main(String[] args) {
        boolean propertyLoggerEnabled = Boolean.parseBoolean(ConfigurationUtils.getStaticValue("gitops.propertyLoggerEnabled", "false"));
        if (propertyLoggerEnabled) {
            SpringApplication springApplication = new SpringApplication(FusionGitOpsServerApplication.class);
            springApplication.addListeners(new PropertiesLogger());
            springApplication.run(args);
        } else {
            SpringApplication.run(FusionGitOpsServerApplication.class, args);
        }
    }

}
