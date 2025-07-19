package ai.tuna.fusion.intgrationtest;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * @author robinqu
 */
@ConfigurationProperties("integration-test")
@Data
public class IntegrationTestProperties {

    public enum KubernetesProviderType {
        AutoDetect,
        TestContainer
    }

    @Data
    public static class Kubernetes {
        private KubernetesProviderType providerType = KubernetesProviderType.AutoDetect;
        private Path kubeConfigPath;
    }

    private Kubernetes kubernetes;

}
