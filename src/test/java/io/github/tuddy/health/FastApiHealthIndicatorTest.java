package io.github.tuddy.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FastApiHealthIndicatorTest {

    private MockRestServiceServer server;
    private FastApiHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://fast-api:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        healthIndicator = new FastApiHealthIndicator(builder.build());
    }

    @DisplayName("FastAPI 정상 응답시 UP")
    @Test
    void FastAPI_정상_응답시_UP() {
        // Given
        server.expect(requestTo("http://fast-api:8000/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        // When & Then
        assertEquals(Status.UP, healthIndicator.health().getStatus());
        server.verify();
    }

    @DisplayName("FastAPI 에러 응답시 DOWN")
    @Test
    void FastAPI_에러_응답시_DOWN() {
        // Given
        server.expect(requestTo("http://fast-api:8000/"))
                .andRespond(withServerError());

        // When & Then
        assertEquals(Status.DOWN, healthIndicator.health().getStatus());
        server.verify();
    }
}