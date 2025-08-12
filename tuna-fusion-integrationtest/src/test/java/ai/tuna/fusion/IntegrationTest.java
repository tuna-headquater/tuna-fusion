package ai.tuna.fusion;

import ai.tuna.fusion.intgrationtest.K8SNamespacedResourceExtension;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.util.Arrays;

import static ai.tuna.fusion.intgrationtest.K8SNamespacedResourceExtension.TEST_NAMESPACE;

/**
 * @author robinqu
 */
@Slf4j
// Declare Spring context
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=reactive"
)
// Prepare test resource like namespace, etc. This should always come after SpringBootTest so that Spring context is initialized first in SpringExtension.
@ExtendWith(K8SNamespacedResourceExtension.class)
// Mark context dirty after each test class
@DirtiesContext
// additional test configuration
@ContextConfiguration(classes = {
        IntegrationTestApplication.class
})
public class IntegrationTest {

    @Autowired
    private ReactiveWebServerApplicationContext server;

    protected int getTestServerPort() {
        return server.getWebServer().getPort();
    }

    @DynamicPropertySource
    public static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        log.info("Configuring namespace properties with ns={}", K8SNamespacedResourceExtension.TEST_NAMESPACE.get());
        Arrays.asList(
                "spring.cloud.kubernetes.client.namespace",
                "javaoperatorsdk.reconcilers.agentDeploymentReconciler.namespaces[0]",
                "javaoperatorsdk.reconcilers.agentEnvironmentReconciler.namespaces[0]",
                "javaoperatorsdk.reconcilers.podFunctionBuildReconciler.namespaces[0]",
                "javaoperatorsdk.reconcilers.podFunctionReconciler.namespaces[0]",
                "javaoperatorsdk.reconcilers.podPoolReconciler.namespaces[0]",
                "gitops.watched-namespaces[0]",
                "executor.informers.namespaces[0]"
        ).forEach(propertyName -> registry.add(propertyName, K8SNamespacedResourceExtension.TEST_NAMESPACE::get));
    }


}
