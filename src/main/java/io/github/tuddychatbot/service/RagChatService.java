package io.github.tuddychatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.github.tuddychatbot.dto.ChatProxyRequest;
import io.github.tuddychatbot.dto.RagChatRequest;


// Controller가 받은 요청 FastAPI로 전송, 응답 바디 (JSON) 반환
@Service
public class RagChatService {
    private final RestClient client;
    private final String chatPath;

    public RagChatService(RestClient ragRestClient,
                          @Value("${rag.api.chat-path:/rag/chat}") String chatPath) {
        this.client = ragRestClient;
        this.chatPath = chatPath;
    }

    // RagChatRequest 생성 및 http 호출
    public String relay(ChatProxyRequest req) {
        try {
            return client.post().uri(chatPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new RagChatRequest(req.userId(), req.query()))
                    .retrieve()
                    .body(String.class); 			// FastAPI의 JSON 그대로 반환
        } catch (RestClientResponseException e) {
            return e.getResponseBodyAsString(); 	// 오류도 그대로 전달
        }
    }
}
