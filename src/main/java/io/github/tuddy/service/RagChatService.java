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

    // S3 업로드 완료 후 FastAPI에 인덱싱 요청
    public void sendOcrRequest(String userId, String fileKey) {
        // FastAPI 엔드포인트가 form-data 형식을 기대하므로 MultiValueMap 사용
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", userId);
        body.add("file_key", fileKey);

        try {
            client.post().uri(ocrPath)
                    .contentType(MediaType.MULTIPART_FORM_DATA) // 또는 APPLICATION_FORM_URLENCODED
                    .body(body)
                    .retrieve()
                    .toBodilessEntity(); // 결과값이 없어도 OK (200 성공 여부만 확인)
        } catch (RestClientResponseException e) {
            // 에러 발생 시 로그를 남기거나 상위 서비스로 예외 전파
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
            return e.getResponseBodyAsString();
        }
    }

    public String relayChatWithImages(String path, FastApiChatRequest request, List<MultipartFile> files) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            builder.part("user_id", request.userId());
            builder.part("session_id", request.sessionId());
            builder.part("query", request.query());
            builder.part("n_turns", String.valueOf(request.nTurns()));

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
            return e.getResponseBodyAsString();
        }
    }

    public String getChatPath() {
        return this.chatPath;
    }

    public String getNormalPath() {
        return this.normalPath;
    }
}