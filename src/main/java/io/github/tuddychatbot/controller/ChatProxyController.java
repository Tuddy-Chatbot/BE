package io.github.tuddychatbot.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddychatbot.dto.ChatProxyRequest;
import io.github.tuddychatbot.security.SecurityUtils;
import io.github.tuddychatbot.service.RagChatService;
import jakarta.validation.Valid;

@RestController
public class ChatProxyController {
  private final RagChatService ragChatService;
  public ChatProxyController(RagChatService ragChatService) { this.ragChatService = ragChatService; }

  @PostMapping(value="/rag/chat",
		  	   consumes=MediaType.APPLICATION_JSON_VALUE,
		  	   produces=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> chat(@Valid @RequestBody ChatProxyRequest req) {
    Long uid = SecurityUtils.requireUserId();
    return ResponseEntity.ok(ragChatService.relay(uid, req.query()));
  }

  @PostMapping(value="/normal-chat",
		  	   consumes=MediaType.APPLICATION_JSON_VALUE,
		  	   produces=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> normalChat(@Valid @RequestBody ChatProxyRequest req) {
    Long uid = SecurityUtils.requireUserId();
    return ResponseEntity.ok(ragChatService.relayNormal(uid, req.query()));
  }
}
