package io.github.tuddy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.security.SecurityUtils;
import io.github.tuddy.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatProxyController {

  private final ChatService chatService;

  @PostMapping(value="/rag", consumes="application/json", produces="application/json") //
  public ResponseEntity<ChatProxyResponse> chat(@Valid @RequestBody ChatProxyRequest req) {
    Long uid = SecurityUtils.requireUserId();
    ChatProxyResponse response = chatService.processRagChat(uid, req);
    return ResponseEntity.ok(response);
  }

  @PostMapping(value="/normal", consumes="application/json", produces="application/json") //
  public ResponseEntity<ChatProxyResponse> normalChat(@Valid @RequestBody ChatProxyRequest req) {
    Long uid = SecurityUtils.requireUserId();
    ChatProxyResponse response = chatService.processNormalChat(uid, req);
    return ResponseEntity.ok(response);
  }

  // TODO: 채팅 기록 조회를 위한 엔드포인트 추가 필요함 : 프론트와 상의 (예: GET /api/chat/sessions 등)
}