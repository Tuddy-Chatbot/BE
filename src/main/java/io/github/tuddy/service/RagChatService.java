package io.github.tuddy.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.github.tuddy.dto.FastApiChatRequest;

@Service
public class RagChatService {
  private final RestClient client;
  private final String chatPath;
  private final String normalPath;

  public RagChatService(@Qualifier("ragRestClient") RestClient client,
                        @Value("${rag.api.chat-path:/rag/chat}") String chatPath,
                        @Value("${rag.api.normal-path:/normal/chat}") String normalPath) {
    this.client = client;
    this.chatPath = chatPath;
    this.normalPath = normalPath;
  }

  // 메서드 이름을 더 명확하게 변경하고, 새로운 DTO를 받도록 수정
  public String relayRag(FastApiChatRequest request) {
    try {
      return client.post().uri(chatPath)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request) // 수정된 요청 DTO 사용
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
	        .body(request) // 수정된 요청 DTO 사용
	        .retrieve()
	        .body(String.class);
	    } catch (RestClientResponseException e) {
	      return e.getResponseBodyAsString();

	  }
	}
}
