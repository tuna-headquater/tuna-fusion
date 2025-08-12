package ai.tuna.fusion;

import ai.tuna.fusion.intgrationtest.IntegrationTestProperties;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * @author robinqu
 */
@SpringBootApplication
@EnableConfigurationProperties(IntegrationTestProperties.class)
@Slf4j
public class IntegrationTestApplication {

    @ConditionalOnProperty(value = "integration-test.kubernetes.provider-type", havingValue = "AutoDetect")
    @Import(Fabric8AutoConfiguration.class)
    @TestConfiguration
    public static class AutoDetectK8SConfig {
        @Bean
        public KubernetesClient kubernetesClient(Config config) {
            var client = new KubernetesClientBuilder().withConfig(config).build();
            return client;
        }
    }


    @ConditionalOnProperty(value = "integration-test.kubernetes.provider-type", havingValue = "TestContainer")
    @TestConfiguration
    public static class TestContainerK8SConfig {
        @Bean(initMethod = "start", destroyMethod = "stop")
        public K3sContainer k3s() {
            return new K3sContainer(DockerImageName.parse("rancher/k3s:v1.21.3-k3s1"))
                    .withAccessToHost(true)
                    .withLogConsumer(new Slf4jLogConsumer(log));
        }

        @Bean
        @ConditionalOnProperty(value = "integration-test.kubernetes.provider-type", havingValue = "TestContainer")
        public KubernetesClient kubernetesClient(K3sContainer k3s) {
            // obtain a kubeconfig file which allows us to connect to k3s
            String kubeConfigYaml = k3s.getKubeConfigYaml();
            // requires io.fabric8:kubernetes-client:5.11.0 or higher
            Config config = Config.fromKubeconfig(kubeConfigYaml);
            var client = new KubernetesClientBuilder().withConfig(config).build();
            return client;
        }
    }
}
