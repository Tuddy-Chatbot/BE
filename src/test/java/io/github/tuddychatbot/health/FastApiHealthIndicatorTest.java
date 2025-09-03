package io.github.tuddychatbot.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FastApiHealthIndicatorTest {
  private MockRestServiceServer server;
  private FastApiHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    indicator = new FastApiHealthIndicator(builder.baseUrl("http://localhost:8089").build());
  }

  @Test
  void up_when_fastapi_200() {
    server.expect(once(), requestTo("http://localhost:8089/"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
    assertEquals(Status.UP, indicator.health().getStatus());
    server.verify();
  }

  @Test
  void down_when_fastapi_error() {
    server.expect(once(), requestTo("http://localhost:8089/"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withServerError());
    assertEquals(Status.DOWN, indicator.health().getStatus());
    server.verify();
  }
}
