package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.FastApiChatRequest;
import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.chat.ChatSession;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.ChatMessageRepository;
import io.github.tuddy.repository.ChatSessionRepository;
import io.github.tuddy.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks
    private ChatService chatService;

    @Mock
    private RagChatService ragChatService;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;

    @Spy // 실제 ObjectMapper의 로직을 사용하기 위해 @Spy로 주입
    private ObjectMapper objectMapper;

    @DisplayName("신규 채팅 요청 처리 (sessionId가 null일 때)")
    @Test
    void 신규_채팅_요청_처리() {
        // Given
        Long userId = 1L;
        var request = new ChatProxyRequest(null, "새로운 질문입니다");
        var user = UserAccount.builder().id(userId).build();
        var newSession = ChatSession.builder().id(100L).userAccount(user).title("새로운 질문입니다").build();
        String botAnswerJson = "{\"response\":\"새로운 답변입니다.\"}";

        given(userAccountRepository.findById(userId)).willReturn(Optional.of(user));
        given(sessionRepository.save(any(ChatSession.class))).willReturn(newSession);
        given(ragChatService.relayRag(any(FastApiChatRequest.class))).willReturn(botAnswerJson);

        // When
        ChatProxyResponse response = chatService.processRagChat(userId, request);

        // Then
        assertThat(response.sessionId()).isEqualTo(100L);
        assertThat(response.answer()).isEqualTo("새로운 답변입니다.");
        verify(sessionRepository, times(1)).save(any(ChatSession.class)); // 새 세션 저장 확인
        verify(messageRepository, times(2)).save(any(ChatMessage.class)); // 사용자, 봇 메시지 2번 저장 확인
    }

    @DisplayName("기존 채팅 요청 처리 (sessionId가 있을 때)")
    @Test
    void 기존_채팅_요청_처리() {
        // Given
        Long userId = 1L;
        Long sessionId = 100L;
        var request = new ChatProxyRequest(sessionId, "이전 대화에 이어서 질문합니다");
        var existingSession = ChatSession.builder().id(sessionId).build();
        String botAnswerJson = "{\"response\":\"이어진 답변입니다.\"}";

        given(sessionRepository.findById(sessionId)).willReturn(Optional.of(existingSession));
        given(ragChatService.relayNormal(any(FastApiChatRequest.class))).willReturn(botAnswerJson);

        // When
        ChatProxyResponse response = chatService.processNormalChat(userId, request);

        // Then
        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.answer()).isEqualTo("이어진 답변입니다.");
        verify(sessionRepository, times(0)).save(any(ChatSession.class)); // 새 세션 저장 안 함
        verify(messageRepository, times(2)).save(any(ChatMessage.class)); // 메시지는 2번 저장
    }
}