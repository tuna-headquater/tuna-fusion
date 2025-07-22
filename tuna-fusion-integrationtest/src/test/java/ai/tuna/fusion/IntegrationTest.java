package ai.tuna.fusion;

import ai.tuna.fusion.intgrationtest.K8SNamespacedResourceExtension;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;

/**
 * @author robinqu
 */
@Slf4j
@ExtendWith(K8SNamespacedResourceExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {
        IntegrationTestApplication.class,
        IntegrationTest.IntegrationTestConfiguration.class
})
public class IntegrationTest {


    @TestConfiguration
    public static class IntegrationTestConfiguration {
        private static void installCRDs(KubernetesClient kubernetesClient) {
            log.info("Installing CRDs...");
            String[] crdFiles = {
                    "agentcatalogues.fusion.tuna.ai-v1.yml",
                    "agentdeployments.fusion.tuna.ai-v1.yml",
                    "agentenvironments.fusion.tuna.ai-v1.yml",
                    "podfunctionbuilds.fusion.tuna.ai-v1.yml",
                    "podfunctions.fusion.tuna.ai-v1.yml",
                    "podpools.fusion.tuna.ai-v1.yml"
            };

            for (String crdFile : crdFiles) {
                // Load from metadata module's generated location
                try (InputStream is = IntegrationTest.class
                        .getResourceAsStream("/META-INF/fabric8/" + crdFile)) {
                    if (is != null) {
                        kubernetesClient.apiextensions().v1().customResourceDefinitions()
                                .load(is)
                                .unlock()
                                .createOr(NonDeletingOperation::update);
                        log.info("Created CRD: {}", crdFile);
                    } else {
                        log.error("CRD file not found: META-INF/fabric8/{}", crdFile);
                    }
                } catch (Exception e) {
                    log.error("Error creating CRD {}: {}", crdFile, e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
        }

        @ConditionalOnProperty(value = "integration-test.kubernetes.provider-type", havingValue = "AutoDetect")
        @Import(Fabric8AutoConfiguration.class)
        @TestConfiguration
        public static class AutoDetectK8SConfig {
            @Bean
            public KubernetesClient kubernetesClient(Config config) {
                var client = new KubernetesClientBuilder().withConfig(config).build();
                installCRDs(client);
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
                installCRDs(client);
                return client;
            }
        }

    }



}
