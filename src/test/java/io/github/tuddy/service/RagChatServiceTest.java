package io.github.tuddy.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.tuddy.dto.FastApiChatRequest;

class RagChatServiceTest {

    private MockRestServiceServer server;
    private RagChatService service;

    // 테스트용 경로
    private static final String RAG_PATH = "/rag/chat";
    private static final String NORMAL_PATH = "/normal/chat";
    private static final String OCR_PATH = "/rag/vectordb/ocr-and-add-from-s3";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://test-rag-api.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new RagChatService(builder.build(), RAG_PATH, NORMAL_PATH, OCR_PATH);
    }

    @DisplayName("1. RAG API 호출 성공 (relayRag)")
    @Test
    void RAG_API_호출_성공() {
        // Given
        var request = new FastApiChatRequest("1", "1", "안녕", 5, List.of("file.pdf"));
        String expectedResponse = "{\"response\":\"RAG 응답\"}";

        server.expect(requestTo("http://test-rag-api.com" + RAG_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(expectedResponse, MediaType.APPLICATION_JSON));

        // When
        String actualResponse = service.relayRag(request);

        // Then
        assertEquals(expectedResponse, actualResponse);
        server.verify();
    }

    @DisplayName("2. OCR 인덱싱 요청 성공 (sendOcrRequest)")
    @Test
    void OCR_인덱싱_요청_성공() {
        // Given
        String userId = "10";
        String fileKey = "incoming/10/test.pdf";

        // FastAPI가 form-data로 'user_id'와 'file_key'를 받는 것을 Mocking
        server.expect(requestTo("http://test-rag-api.com" + OCR_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess()); // 200 OK 응답

        // When & Then
        assertDoesNotThrow(() -> service.sendOcrRequest(userId, fileKey));
        server.verify();
    }

    @DisplayName("3. OCR 인덱싱 요청 실패 시 RuntimeException 발생")
    @Test
    void OCR_인덱싱_요청_실패() {
        // Given
        String userId = "10";
        String fileKey = "incoming/10/test.pdf";

        server.expect(requestTo("http://test-rag-api.com" + OCR_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("{\"error\":\"S3 key not found\"}"));

        // When & Then
        assertThrows(RuntimeException.class, () -> service.sendOcrRequest(userId, fileKey));
        server.verify();
    }

    @DisplayName("4. 이미지 첨부 요청 성공 (relayChatWithImages)")
    @Test
    void 이미지_첨부_요청_성공() {
        // Given: 이미지 파일은 MockRestServiceServer로 테스트하기 복잡하므로,
        // 응답만 Mock하고 호출이 성공하는지 확인합니다.
        var request = new FastApiChatRequest("1", "1", "이미지 질문", 5, Collections.emptyList());
        List<org.springframework.web.multipart.MultipartFile> emptyFiles = Collections.emptyList();

        String expectedResponse = "{\"response\":\"이미지 응답\"}";

        server.expect(requestTo("http://test-rag-api.com" + NORMAL_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(expectedResponse, MediaType.APPLICATION_JSON));

        // When
        String actualResponse = service.relayChatWithImages(NORMAL_PATH, request, emptyFiles);

        // Then
        assertEquals(expectedResponse, actualResponse);
        server.verify();
    }
}