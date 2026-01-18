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

    // Multipart 채팅 요청 (ChatService 사용)
    public String relayChatWithImages(String path, FastApiChatRequest req, List<MultipartFile> files) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            // Python 규격(snake_case)에 맞춰 파라미터 전송
            builder.part("user_id", req.userId());
            builder.part("query", req.query());
            builder.part("n_turns", String.valueOf(req.nTurns()));

            if (req.sessionId() != null) {
                builder.part("session_id", req.sessionId());
            }

            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    builder.part("files", file.getResource());
                }
            }

            return client.post().uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(String.class);

        } catch (Exception e) {
            log.error("AI Chat Error", e);
            return "{\"response\": \"AI 서버 오류: " + e.getMessage() + "\"}";
        }
    }

    // OCR 요청 (FileService 사용)
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
        } catch (Exception e) {
            throw new RuntimeException("OCR Request Failed", e);
        }
    }

    // [Restored] Legacy 메서드들
    public String relayRag(FastApiChatRequest request) {
        return client.post().uri(chatPath).contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(String.class);
    }

    public String relayNormal(FastApiChatRequest request) {
        return client.post().uri(normalPath).contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(String.class);
    }
}