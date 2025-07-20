package ai.tuna.fusion.intgrationtest;

import ai.tuna.fusion.IntegrationTest;
import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.extension.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author robinqu
 */
@Slf4j
public class K8SNamespacedResourceExtension implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback, ParameterResolver {

    @Getter
    private static KubernetesClient kubernetesClient;

    @Getter
    private static TestResourceContext resourceLoader;

    private String testNamespace;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(TestResourceContext.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().equals(TestResourceContext.class)) {
            return resourceLoader;
        }
        throw new ParameterResolutionException("Unsupported parameter type: " + parameterContext.getParameter().getType());
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        log.info("Get kubernetesClient from Spring context");
        var ctx = SpringExtension.getApplicationContext(context);
        kubernetesClient = ctx.getBean(KubernetesClient.class);
        Preconditions.checkNotNull(kubernetesClient, "should have valid Kubernetes client in application context");

        testNamespace = "test-" + UUID.randomUUID();
        log.info("Ensuring namespace...");
        var ns = new NamespaceBuilder().withNewMetadata().withName(testNamespace).endMetadata().build();
        kubernetesClient.namespaces().resource(ns).create();
        log.info("Namespace created: {}", testNamespace);
        resourceLoader = new TestResourceContext(kubernetesClient, testNamespace);
        installNamespacedResources();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        log.info("Delete namespace...");
        kubernetesClient.namespaces().withName(testNamespace).withTimeout(30, TimeUnit.SECONDS).delete();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {

    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {

    }

    private void installNamespacedResources() {
        log.info("Installing required resources...");
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
