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

    // [RAG Chat] 파일이 있을 때 (Multipart + File)
    public String relayChatWithImages(String path, FastApiChatRequest req, List<MultipartFile> files) {
        return sendMultipartRequest(path, req, files);
    }

    // [Normal Chat] 파일이 없을 때 (Multipart Only - No File)
    // 수정: FastAPI가 Form 데이터를 요구하므로 JSON 대신 Multipart로 전송
    public String relayNormal(FastApiChatRequest req) {
        return sendMultipartRequest(normalPath, req, null);
    }

    // 공통 요청 메서드 (중복 제거)
    private String sendMultipartRequest(String path, FastApiChatRequest req, List<MultipartFile> files) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            // FastAPI Form(...) 필드 매핑
            builder.part("user_id", req.userId());
            builder.part("query", req.query());
            builder.part("n_turns", String.valueOf(req.nTurns()));

            if (req.sessionId() != null) {
                builder.part("session_id", req.sessionId());
            }

            // 파일이 있는 경우에만 추가
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

        } catch (RestClientResponseException e) {
            log.error("AI Server Error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return makeErrorJson(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("AI Connection Error", e);
            return makeErrorJson(e.getMessage());
        }
    }

    // [OCR] 파일 업로드 시 인덱싱 요청
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
            log.info("OCR Request Sent: {}", fileKey);
        } catch (Exception e) {
            log.error("OCR Request Failed", e);
        }
    }

    private String makeErrorJson(String msg) {
        String safeMsg = (msg == null) ? "Unknown Error" : msg.replace("\"", "'").replace("\n", " ");
        return "{\"response\": \"AI 서버 오류: " + safeMsg + "\"}";
    }

    // Legacy
    public String relayRag(FastApiChatRequest request) {
        return relayNormal(request);
    }
}