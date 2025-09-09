package io.github.tuddy.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.github.tuddy.dto.RagChatRequest;

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

  public String relay(Long userId, String query) {
    String namespace = String.valueOf(userId);
    try {
      return client.post().uri(chatPath)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(new RagChatRequest(namespace, query))
        .retrieve()
        .body(String.class);
    } catch (RestClientResponseException e) {
      return e.getResponseBodyAsString();
    }
  }

  public String relayNormal(Long userId, String query) {
    String namespace = String.valueOf(userId);
    try {
      return client.post().uri(normalPath)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(new RagChatRequest(namespace, query))
        .retrieve()
        .body(String.class);
    } catch (RestClientResponseException e) {
      return e.getResponseBodyAsString();
    }
  }
}
