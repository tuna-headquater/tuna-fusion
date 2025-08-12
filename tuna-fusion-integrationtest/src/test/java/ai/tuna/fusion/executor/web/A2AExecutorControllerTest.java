package ai.tuna.fusion.executor.web;

import ai.tuna.fusion.IntegrationTest;
import ai.tuna.fusion.TestResourceGroups;
import ai.tuna.fusion.intgrationtest.TestResourceContext;
import ai.tuna.fusion.metadata.a2a.AgentCard;
import io.a2a.spec.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author robinqu
 */
@Slf4j
@AutoConfigureWebTestClient
public class A2AExecutorControllerTest extends IntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebTestClient webClient;


    @Test
    void testA2AClientExecution(TestResourceContext context) throws A2AClientError, A2AServerException, JsonProcessingException, InterruptedException {
        context.awaitResourceGroup(TestResourceGroups.RESOURCE_GROUP_2);
        var agentUrl = "http://localhost:%s/a2a/namespaces/%s/agents/test-deploy-1".formatted(
                getTestServerPort(),
                context.targetNamespace()
        );
        log.info("agentUrl={}", agentUrl);

        webClient.get()
                .uri(agentUrl + "/.well-known/agent.json")
                .exchange()
                .expectBody(AgentCard.class)
                .value(agentCard -> {
                    assertThat(agentCard).isNotNull();
                    assertThat(agentCard.getName()).isEqualTo("agent1");
                });


        var req1 = new SendMessageRequest.Builder()
                .jsonrpc("2.0").method("message/send")
                .params(new MessageSendParams.Builder()
                        .message(new Message.Builder()
                                .role(Message.Role.USER)
                                .parts(new TextPart("hello"))
                                .build())
                        .build())
                .build();


        webClient.post().uri(agentUrl)
                .body(BodyInserters.fromValue(req1))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(SendMessageResponse.class)
                .value(response -> {
                    assertThat(response).isNotNull();
                    var result = response.getResult();
                    try {
                        assertThat(objectMapper.writeValueAsString(result)).contains("hello world");
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });


        var req2 = (new SendStreamingMessageRequest.Builder()).jsonrpc("2.0").method("message/stream").params(req1.getParams()).build();
        var buffered = webClient.post().uri(agentUrl)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromValue(req2))
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .reduce((s1, s2) -> s1 + s2)
                .block();
        log.info("buffered: {}", buffered);
        assertThat(buffered).contains("hello world");
    }

}
