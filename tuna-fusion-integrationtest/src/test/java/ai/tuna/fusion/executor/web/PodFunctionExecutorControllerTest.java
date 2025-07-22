package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.IntegrationTest;
import ai.tuna.fusion.TestResourceGroups;
import ai.tuna.fusion.intgrationtest.TestResourceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author robinqu
 */
@AutoConfigureWebTestClient
public class PodFunctionExecutorControllerTest extends IntegrationTest {

    @Autowired
    private WebTestClient webClient;

    /**
     * This test will load function whose source code is located in  <a href="https://gist.github.com/RobinQu/f8f755f8bb0807ad564662c637175d23">gist.</a>
     */
    @Test
    void testFunctionExecution(TestResourceContext context) {
        context.awaitResourceGroup(TestResourceGroups.RESOURCE_GROUP_1);
        webClient.get()
                .uri("/namespaces/%s/functions/%s".formatted(context.targetNamespace(), "test-function-1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Hello, World!");
    }
}
