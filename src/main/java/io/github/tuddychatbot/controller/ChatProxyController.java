package io.github.tuddychatbot.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddychatbot.dto.ChatProxyRequest;
import io.github.tuddychatbot.service.RagChatService;
import jakarta.validation.Valid;


// POST 엔드포인트가 JSON 요청을 받아 서비스에 위임하고, 서비스가 돌려준 JSON 문자열을 그대로 응답
@RestController
@RequestMapping(path="/api", produces=MediaType.APPLICATION_JSON_VALUE)
public class ChatProxyController {

    private final RagChatService ragChatService;
    public ChatProxyController(RagChatService ragChatService){ this.ragChatService = ragChatService; }

    // 본문이 JSON이 아닌 경우, 415 거절
    // RAG 사용
    @PostMapping(value = "/chat",
    			 consumes=MediaType.APPLICATION_JSON_VALUE,
    			 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@Valid @RequestBody ChatProxyRequest req){
        return ResponseEntity.ok(ragChatService.relay(req));
    }

    // 일반 응답
    @PostMapping(value = "/normal-chat",
    			consumes = MediaType.APPLICATION_JSON_VALUE,
    			produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> normalChat(@Valid @RequestBody ChatProxyRequest req) {
        return ResponseEntity.ok(ragChatService.relayNormal(req));
    }
}
