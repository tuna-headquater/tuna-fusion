package ai.tuna.fusion;

import ai.tuna.fusion.intgrationtest.TestResourceLoader;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author robinqu
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {IntegrationTestApplication.class, IntegrationTest.IntegrationTestConfiguration.class}, initializers = IntegrationTest.TestContextInitializer.class)
public class IntegrationTest {

    public static String TEST_NAMESPACE = "test-" + UUID.randomUUID();

    protected String getTestNamespace() {
        return TEST_NAMESPACE;
    }

    public static class TestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
            setupCustomProperties(applicationContext);
        }

        private void setupCustomProperties(ConfigurableApplicationContext applicationContext) {
            log.info("Setting up custom properties for integration tests");
            TestPropertyValues.of(
                    "test_namespace", TEST_NAMESPACE
            ).applyTo(applicationContext);
        }
    }

    @TestConfiguration
    public static class IntegrationTestConfiguration {

        @Bean
        public TestResourceLoader resourceLoader(KubernetesClient kubernetesClient) {
            return new TestResourceLoader(kubernetesClient, TEST_NAMESPACE);
        }

        @ConditionalOnProperty(value = "integration-test.kubernetes.provider-type", havingValue = "AutoDetect")
        @Import(Fabric8AutoConfiguration.class)
        @TestConfiguration
        public static class AutoDetectK8SConfig {
            @Bean
            public KubernetesClient kubernetesClient(Config config) {
                var client = new KubernetesClientBuilder().withConfig(config).build();
                configureKubernetesResources(client);
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
                configureKubernetesResources(client);
                return client;
            }
        }

        private static void configureKubernetesResources(KubernetesClient client) {
            installCRDs(client);
//            installRequiredResources(client);
        }


        private static void installCRDs(KubernetesClient client) {
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
                        client.apiextensions().v1().customResourceDefinitions()
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

        private static void installRequiredResources(KubernetesClient client) {
            log.info("Installing required resources...");
            String[] resourceLists = {
                    "yaml/shared/fusion_builder_rbac.yaml",
                    "yaml/shared/shared_archive_pvc.yaml"
            };
            for (String resourceListFile : resourceLists) {
                try (InputStream is = IntegrationTest.class.getClassLoader()
                        .getResourceAsStream(resourceListFile)) {
                    if (is != null) {
                        client.load(is)
                                .inNamespace(TEST_NAMESPACE)
                                .createOrReplace();
                    } else {
                        throw new IOException("Resource file not found: " + resourceListFile);
                    }
                } catch (Exception e) {
                    log.error("Failed to open resource file: {}", resourceListFile, e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Autowired
    private KubernetesClient client;

    @BeforeEach
    void setupNamespace() {
        log.info("Ensuring namespace...");
        var ns = new NamespaceBuilder().withNewMetadata().withName(TEST_NAMESPACE).endMetadata().build();
        client.namespaces().resource(ns).create();
        log.info("Re-created namespace: {}", TEST_NAMESPACE);
        IntegrationTestConfiguration.installRequiredResources(client);
    }

    @AfterEach
    void teardownNamespace() {
        log.info("Delete namespace...");
        client.namespaces().withName(TEST_NAMESPACE).withTimeout(30, TimeUnit.SECONDS).delete();
    }


}
