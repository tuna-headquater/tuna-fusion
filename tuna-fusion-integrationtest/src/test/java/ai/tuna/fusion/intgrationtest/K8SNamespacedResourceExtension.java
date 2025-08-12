package ai.tuna.fusion.intgrationtest;

import ai.tuna.fusion.IntegrationTest;
import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.extension.*;
import org.springframework.context.Lifecycle;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author robinqu
 */
@Slf4j
public class K8SNamespacedResourceExtension implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback, ParameterResolver {

    public static final ThreadLocal<String> TEST_NAMESPACE = new ThreadLocal<>();

    private static final String RESOURCE_LOADER_KEY = "resource-loader";
    private static final String KUBERNETES_CLIENT_KEY = "kubernetes-client";


    private TestResourcesWithLifecycle testResources;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(TestResourceContext.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().equals(TestResourceContext.class)) {
            return extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(RESOURCE_LOADER_KEY);
        }
        throw new ParameterResolutionException("Unsupported parameter type: " + parameterContext.getParameter().getType());
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var testNamespace = "test-" + UUID.randomUUID();
        TEST_NAMESPACE.set(testNamespace);

        log.info("Get kubernetesClient from Spring context");
        var ctx = SpringExtension.getApplicationContext(context);
        var kubernetesClient = copyClient(ctx.getBean(KubernetesClient.class));
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(KUBERNETES_CLIENT_KEY, kubernetesClient);
        testResources = new TestResourcesWithLifecycle(kubernetesClient, testNamespace);
        testResources.start();

        Preconditions.checkNotNull(kubernetesClient, "should have valid Kubernetes client in application context");
        var resourceLoader = new TestResourceContext(kubernetesClient, testNamespace);
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(RESOURCE_LOADER_KEY, resourceLoader);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        testResources.stop();
        Optional.ofNullable(context.getStore(ExtensionContext.Namespace.GLOBAL).get(KUBERNETES_CLIENT_KEY))
                .ifPresent(client -> ((KubernetesClient)client).close());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {

    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {

    }

    private KubernetesClient copyClient(KubernetesClient client) {
        // copy client instance in case original instance is closed by Spring context
        var existingConfig = client.getConfiguration();
        return new KubernetesClientBuilder()
                .withConfig(existingConfig)
                .build();
    }


    public static class TestResourcesWithLifecycle implements Lifecycle {
        private final KubernetesClient kubernetesClient;
        private final AtomicBoolean started;
        private final String testNamespace;

        public TestResourcesWithLifecycle(KubernetesClient kubernetesClient, String testNamespace) {
            this.kubernetesClient = kubernetesClient;
            this.started = new AtomicBoolean(false);
            this.testNamespace = testNamespace;
        }

        @Override
        public void start() {
            ensureNamespace();
            installCRDs();
            installNamespacedResources();
            this.started.set(true);
        }

        @Override
        public void stop() {
            cleanupNamespace();
            kubernetesClient.close();
            this.started.set(false);
        }

        @Override
        public boolean isRunning() {
            return started.get();
        }

        private void ensureNamespace() {
            log.info("Ensuring namespace...");
            var ns = new NamespaceBuilder().withNewMetadata().withName(testNamespace).endMetadata().build();
            kubernetesClient.namespaces().resource(ns).create();
            log.info("Namespace created: {}", testNamespace);
        }

        private void cleanupNamespace() {
            log.info("Delete namespace: {}", testNamespace);
            kubernetesClient.namespaces().withName(testNamespace)
                    .withTimeout(30, TimeUnit.SECONDS)
                    .delete();
        }

        private void installCRDs() {
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


        private void installNamespacedResources() {
            log.info("Installing shared namespaced resources...");
            String[] resourceLists = {
                    "yaml/shared/fusion_builder_rbac.yaml",
                    "yaml/shared/shared_archive_pvc.yaml"
            };
            for (String resourceListFile : resourceLists) {
                try (InputStream is = IntegrationTest.class.getClassLoader()
                        .getResourceAsStream(resourceListFile)) {
                    if (is != null) {
                        var content = postProcessResourceContent(IOUtils.toString(is, StandardCharsets.UTF_8));
                        kubernetesClient.resourceList(content)
                                .inNamespace(testNamespace)
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

        private String postProcessResourceContent(String content) {
            var sub = new StringSubstitutor(Map.of(
                    "testNamespace", testNamespace
            ));
            return sub.replace(content);

        }

    }



}
