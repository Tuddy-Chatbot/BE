package io.github.tuddy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.FastApiChatRequest;

class RagChatServiceTest {

    private MockRestServiceServer server;
    private RagChatService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://test-rag-api.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new RagChatService(builder.build(), "/rag/chat", "/normal/chat");
    }

    @DisplayName("RAG 채팅 API 호출 성공")
    @Test
    void RAG_채팅_API_호출_성공() throws Exception {
        // Given
        var request = new FastApiChatRequest("1", "1", "안녕", 5);
        String expectedResponse = "{\"response\":\"RAG 응답\"}";

        server.expect(requestTo("http://test-rag-api.com/rag/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(expectedResponse, MediaType.APPLICATION_JSON));

        // When
        String actualResponse = service.relayRag(request);

        // Then
        assertEquals(expectedResponse, actualResponse);
        server.verify();
    }

    @DisplayName("RAG 채팅 API 호출 실패(4xx)")
    @Test
    void RAG_채팅_API_호출_실패() {
        // Given
        var request = new FastApiChatRequest("1", "1", "잘못된 요청", 5);
        String errorResponse = "{\"error\":\"Bad Request\"}";

        server.expect(requestTo("http://test-rag-api.com/rag/chat"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(errorResponse));

        // When
        String actualResponse = service.relayRag(request);

        // Then
        assertEquals(errorResponse, actualResponse);
        server.verify();
    }
}