package io.github.tuddy.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.FastApiChatRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RagChatService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // RagClientConfig에서 등록한 Bean 이름("ragRestClient") 사용
    public RagChatService(@Qualifier("ragRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String getNormalPath() { return "/rag/chat/normal"; }
    public String getChatPath() { return "/rag/chat"; }

    public String relayChatWithImages(String apiPath, FastApiChatRequest req, List<MultipartFile> files) {
        // Multipart Body 생성
        var builder = new org.springframework.http.client.MultipartBodyBuilder();

        // [핵심] Python 파라미터 이름(snake_case)으로 매핑
        builder.part("user_id", req.userId());
        builder.part("query", req.query());
        builder.part("n_turns", req.nTurns());

        if (req.sessionId() != null) {
            builder.part("session_id", req.sessionId());
        }

        // 파일 추가
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                builder.part("files", file.getResource());
            }
        }

        try {
            return restClient.post()
                    .uri(apiPath)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("AI Server Error: {}", e.getMessage(), e);
            return "{\"response\": \"AI 서버 연결 실패: " + e.getMessage() + "\"}";
        }
    }

    // OCR 요청 등 다른 메서드는 필요시 구현
    public void sendOcrRequest(String userId, String s3Key) { /* 구현 생략 */ }
}