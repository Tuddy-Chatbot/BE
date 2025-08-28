package io.github.tuddychatbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.tuddychatbot.dto.ChatProxyRequest;

class RagChatServiceTest {

    private MockRestServiceServer server;
    private RagChatService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // RestClient.Builder 에 바인딩해야 요청이 인터셉트됨
        server = MockRestServiceServer.bindTo(builder).build();

        RestClient client = builder.baseUrl("http://localhost:8089").build();
        service = new RagChatService(client, "/rag/chat");
    }

    @Test
    void relay_success_returns_body_as_is() {
        String expected = "{\"answer\":\"ok\"}";

        server.expect(once(), requestTo("http://localhost:8089/rag/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(expected, MediaType.APPLICATION_JSON));

        String body = service.relay(new ChatProxyRequest("alice", "hi"));
        assertEquals(expected, body);

        server.verify();
    }

    @Test
    void relay_4xx_returns_error_body_as_is() {
        String errorJson = "{\"detail\":\"bad request\"}";

        server.expect(once(), requestTo("http://localhost:8089/rag/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(errorJson));

        String body = service.relay(new ChatProxyRequest("alice", "bad"));
        assertEquals(errorJson, body);

        server.verify();
    }
}
