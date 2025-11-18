package io.github.tuddy.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    @Operation(summary = "챗봇과 대화", description = "챗봇과 대화 : 이미지 파일을 첨부할 수 있으며, 요청 시 fileId를 포함하면 RAG 기반으로 동작")
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE }) // 어떤 타입의 요청을 받을지 명시
    public ResponseEntity<ChatProxyResponse> chat(
            @RequestPart("req") @Valid ChatProxyRequest req,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        Long uid = SecurityUtils.requireUserId();

        // ChatService의 파라미터가 확장된 processChat 메서드를 호출합니다.
        ChatProxyResponse response = chatService.processChat(uid, req, files);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 채팅방 목록 조회", description = "현재 로그인한 사용자의 모든 채팅방 목록을 최신순으로 조회")
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getMyChatSessions() {
        Long userId = SecurityUtils.requireUserId();
        List<ChatSessionResponse> sessions = chatService.getMyChatSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "특정 채팅방의 메시지 전체 조회", description = "특정 채팅방에 속한 모든 메시지를 시간순으로 조회")
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getChatMessages(@PathVariable Long sessionId) {
        Long userId = SecurityUtils.requireUserId();
        List<ChatMessageResponse> messages = chatService.getMessagesBySession(userId, sessionId);
        return ResponseEntity.ok(messages);
    }

    // ex) GET /chat/sessions/1/messages?page=0&size=20
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Slice<ChatMessageResponse>> getChatMessages(
            @PathVariable Long sessionId,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = SecurityUtils.requireUserId();
        Slice<ChatMessageResponse> messages = chatService.getMessagesBySession(userId, sessionId, pageable);
        return ResponseEntity.ok(messages);
    }

}

