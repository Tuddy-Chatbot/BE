package io.github.tuddy.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.dto.ChatMessageResponse;
import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.ChatSessionResponse;
import io.github.tuddy.security.SecurityUtils;
import io.github.tuddy.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "채팅 API", description = "챗봇과의 대화 및 기록 관리 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatProxyController {

    private final ChatService chatService;

    // /rag와 /normal 엔드포인트를 하나로 통합한 단일 엔드포인트
    @Operation(summary = "챗봇과 대화", description = "챗봇과 대화를 시작하거나 이어갑니다. 요청 시 fileId를 포함하면 RAG 기반으로, 포함하지 않으면 일반 대화로 동작합니다.")
    @PostMapping
    public ResponseEntity<ChatProxyResponse> chat(@Valid @RequestBody ChatProxyRequest req) {
        Long uid = SecurityUtils.requireUserId();
        // ★★★ 새로 만든 통합 서비스 메서드를 호출합니다. ★★★
        ChatProxyResponse response = chatService.processChat(uid, req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 채팅방 목록 조회", description = "현재 로그인한 사용자의 모든 채팅방 목록을 최신순으로 조회합니다.")
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getMyChatSessions() {
        Long userId = SecurityUtils.requireUserId();
        List<ChatSessionResponse> sessions = chatService.getMyChatSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "특정 채팅방의 메시지 전체 조회", description = "특정 채팅방에 속한 모든 메시지를 시간순으로 조회합니다.")
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getChatMessages(@PathVariable Long sessionId) {
        Long userId = SecurityUtils.requireUserId();
        List<ChatMessageResponse> messages = chatService.getMessagesBySession(userId, sessionId);
        return ResponseEntity.ok(messages);
    }
}

