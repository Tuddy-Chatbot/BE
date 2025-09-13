/* package io.github.tuddychatbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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

import io.github.tuddy.service.RagChatService;

class RagChatServiceTest {

  private MockRestServiceServer server;
  private RagChatService service;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient client = builder.baseUrl("http://localhost:8089").build();
    service = new RagChatService(client, "/rag/chat", "/normal/chat");
  }

  @Test
  void relay_success_sends_namespace_and_returns_body() {
    String expectedReq = "{\"namespace\":\"123\",\"query\":\"hi\"}";
    String expectedRes = "{\"answer\":\"ok\"}";

    server.expect(once(), requestTo("http://localhost:8089/rag/chat"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
      .andExpect(content().json(expectedReq))
      .andRespond(withSuccess(expectedRes, MediaType.APPLICATION_JSON));

    String body = service.relay(123L, "hi");
    assertEquals(expectedRes, body);

    server.verify();
  }

  @Test
  void relay_4xx_returns_error_body_as_is() {
    String err = "{\"detail\":\"bad request\"}";

    server.expect(once(), requestTo("http://localhost:8089/rag/chat"))
      .andExpect(method(HttpMethod.POST))
      .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(err));

    String body = service.relay(123L, "bad");
    assertEquals(err, body);

    server.verify();
  }

  @Test
  void relayNormal_success_sends_namespace_and_returns_body() {
    String expectedReq = "{\"namespace\":\"123\",\"query\":\"hi\"}";

    server.expect(once(), requestTo("http://localhost:8089/normal/chat"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
      .andExpect(content().json(expectedReq))
      .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

    String body = service.relayNormal(123L, "hi");
    assertEquals("{\"ok\":true}", body);

    server.verify();
  }

  @Test
  void relayNormal_4xx_returns_error_body_as_is() {
    String err = "{\"detail\":\"bad request\"}";

    server.expect(once(), requestTo("http://localhost:8089/normal/chat"))
      .andExpect(method(HttpMethod.POST))
      .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(err));

    String body = service.relayNormal(123L, "bad");
    assertEquals(err, body);

    server.verify();
  }
}
*/