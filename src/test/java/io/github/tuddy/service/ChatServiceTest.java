package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.FastApiChatRequest;
import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.chat.ChatSession;
import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.ChatMessageRepository;
import io.github.tuddy.repository.ChatSessionRepository;
import io.github.tuddy.repository.UploadedFileRepository;
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
    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Spy
    private ObjectMapper objectMapper;

    @DisplayName("신규 일반 채팅 요청 (텍스트 전용)")
    @Test
    void 신규_일반_채팅_요청() {
        // Given
        Long userId = 1L;
        var request = new ChatProxyRequest(null, "새로운 질문입니다", null);
        var user = UserAccount.builder().id(userId).build();
        var newSession = ChatSession.builder().id(100L).userAccount(user).title("새로운 질문입니다").build();
        String botAnswerJson = "{\"response\":\"일반 답변입니다.\"}";

        given(userAccountRepository.findById(userId)).willReturn(Optional.of(user));
        given(sessionRepository.save(any(ChatSession.class))).willReturn(newSession);
        given(ragChatService.getNormalPath()).willReturn("/normal/chat");
        given(ragChatService.relayNormal(any(FastApiChatRequest.class))).willReturn(botAnswerJson);

        // When
        ChatProxyResponse response = chatService.processChat(userId, request, Collections.emptyList());

        // Then
        assertThat(response.sessionId()).isEqualTo(100L);
        assertThat(response.answer()).isEqualTo("일반 답변입니다.");
        verify(sessionRepository, times(1)).save(any(ChatSession.class));
        verify(messageRepository, times(2)).save(any(ChatMessage.class));
    }

    @DisplayName("신규 RAG 채팅 요청 (텍스트 전용)")
    @Test
    void 신규_RAG_채팅_요청() {
        // Given
        Long userId = 1L;
        Long fileId = 10L;
        var request = new ChatProxyRequest(null, "파일에 대해 질문합니다", fileId);
        var user = UserAccount.builder().id(userId).build();
        var file = UploadedFile.builder().id(fileId).build();
        var newSession = ChatSession.builder().id(101L).userAccount(user).uploadedFile(file).build();
        String botAnswerJson = "{\"response\":\"RAG 답변입니다.\"}";

        given(userAccountRepository.findById(userId)).willReturn(Optional.of(user));
        given(uploadedFileRepository.findByIdAndUserAccountId(fileId, userId)).willReturn(Optional.of(file));
        given(sessionRepository.save(any(ChatSession.class))).willReturn(newSession);
        given(ragChatService.getChatPath()).willReturn("/rag/chat");
        given(ragChatService.relayRag(any(FastApiChatRequest.class))).willReturn(botAnswerJson);

        // When
        ChatProxyResponse response = chatService.processChat(userId, request, Collections.emptyList());

        // Then
        assertThat(response.sessionId()).isEqualTo(101L);
        assertThat(response.answer()).isEqualTo("RAG 답변입니다.");
    }

    @DisplayName("이미지 첨부 채팅 요청 (일반 채팅)")
    @Test
    void 이미지_첨부_채팅_요청() {
        // Given
        Long userId = 1L;
        var request = new ChatProxyRequest(null, "이 이미지 뭐야?", null);
        var user = UserAccount.builder().id(userId).build();
        var newSession = ChatSession.builder().id(102L).userAccount(user).build();
        String botAnswerJson = "{\"response\":\"이미지 분석 결과입니다.\"}";

        var imageFile = new MockMultipartFile("image", "test.jpg", "image/jpeg", "test data".getBytes());

        List<MultipartFile> files = List.of(imageFile);

        given(userAccountRepository.findById(userId)).willReturn(Optional.of(user));
        given(sessionRepository.save(any(ChatSession.class))).willReturn(newSession);
        given(ragChatService.getNormalPath()).willReturn("/normal/chat");
        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), any())).willReturn(botAnswerJson);

        // When
        ChatProxyResponse response = chatService.processChat(userId, request, files);

        // Then
        assertThat(response.sessionId()).isEqualTo(102L);
        assertThat(response.answer()).isEqualTo("이미지 분석 결과입니다.");
        verify(ragChatService, times(1)).relayChatWithImages(anyString(), any(), any());
    }
}