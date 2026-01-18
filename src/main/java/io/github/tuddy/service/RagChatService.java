package io.github.tuddy.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import io.github.tuddy.dto.FastApiChatRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RagChatService {

    private final RestClient client;

    private final String chatPath;
    private final String normalPath;
    private final String ocrPath;

    public RagChatService(@Qualifier("ragRestClient") RestClient client,
                          @Value("${rag.api.chat-path:/rag/chat}") String chatPath,
                          @Value("${rag.api.normal-path:/normal/chat}") String normalPath,
                          @Value("${rag.api.ocr-path:/rag/vectordb/ocr-and-add-from-s3}") String ocrPath) {
        this.client = client;
        this.chatPath = chatPath;
        this.normalPath = normalPath;
        this.ocrPath = ocrPath;
    }

    public String getChatPath() { return this.chatPath; }
    public String getNormalPath() { return this.normalPath; }


    public void sendOcrRequest(String userId, String fileKey) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", userId);
        body.add("file_key", fileKey);

        try {
            client.post().uri(ocrPath)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("OCR request sent for file: {}", fileKey);
        } catch (RestClientResponseException e) {
            log.error("OCR Request Failed", e);
            throw new RuntimeException("FastAPI OCR Request Failed: " + e.getResponseBodyAsString());
        }
    }


    public String relayRag(FastApiChatRequest request) {
        try {
            return client.post().uri(chatPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("Relay RAG Failed", e);
            return e.getResponseBodyAsString();
        }
    }


    public String relayNormal(FastApiChatRequest request) {
        try {
            return client.post().uri(normalPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("Relay Normal Failed", e);
            return e.getResponseBodyAsString();
        }
    }

    public String relayChatWithImages(String path, FastApiChatRequest req, List<MultipartFile> files) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            // [핵심] Python 파라미터 이름(snake_case)으로 매핑
            // DTO가 Record이므로 getter 대신 메서드 호출 (req.userId())
            builder.part("user_id", req.userId());
            builder.part("query", req.query());
            builder.part("n_turns", String.valueOf(req.nTurns()));

            if (req.sessionId() != null) {
                builder.part("session_id", req.sessionId());
            }

            // 파일 추가
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    builder.part("files", file.getResource());
                }
            }

            return client.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(String.class);

        } catch (RestClientResponseException e) {
            log.error("AI Server Error: {}", e.getMessage(), e);
            // 프론트엔드 크래시 방지를 위한 JSON 에러 반환
            return "{\"response\": \"AI 서버 오류: " + e.getResponseBodyAsString() + "\"}";
        } catch (Exception e) {
            log.error("General Error", e);
            return "{\"response\": \"내부 서버 오류: " + e.getMessage() + "\"}";
        }
    }
}