package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.FastApiChatRequest;
import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.chat.ChatSession;
import io.github.tuddy.entity.file.FileStatus;
import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.ChatMessageRepository;
import io.github.tuddy.repository.ChatSessionRepository;
import io.github.tuddy.repository.UploadedFileRepository;
import io.github.tuddy.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 100L;
    private static final Long FILE_ID_A = 10L;

    @InjectMocks private ChatService chatService;

    @Mock private RagChatService ragChatService;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private UploadedFileRepository uploadedFileRepository;
    @Spy private ObjectMapper objectMapper;

    @Captor private ArgumentCaptor<ChatMessage> messageCaptor;
    @Captor private ArgumentCaptor<FastApiChatRequest> fastApiRequestCaptor;

    private UserAccount mockUser;
    private ChatSession mockSession;
    private UploadedFile mockFileA;

    @BeforeEach
    void setUp() {
        mockUser = UserAccount.builder().id(USER_ID).build();
        mockSession = ChatSession.builder().id(SESSION_ID).userAccount(mockUser).build();
        mockFileA = UploadedFile.builder().id(FILE_ID_A).originalFilename("file_A.pdf").status(FileStatus.COMPLETED).build();

        lenient().when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
        lenient().when(ragChatService.getChatPath()).thenReturn("/rag/chat");
        lenient().when(ragChatService.getNormalPath()).thenReturn("/normal/chat");
    }

    @DisplayName("1. 신규 채팅 생성 및 일반 대화 (Normal Chat)")
    @Test
    void 신규_채팅_일반_대화() {
        var request = new ChatProxyRequest(null, "새 질문", 0L);
        var newSession = ChatSession.builder().id(101L).userAccount(mockUser).title("새 질문").build();

        given(sessionRepository.save(any(ChatSession.class))).willReturn(newSession);
        given(messageRepository.findFilesBySessionIdAndStatus(101L, FileStatus.COMPLETED))
            .willReturn(Collections.emptyList());
        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"일반 답변\"}");

        chatService.processChat(USER_ID, request, Collections.emptyList());

        verify(ragChatService).relayChatWithImages(eq("/normal/chat"), any(FastApiChatRequest.class), anyList());
        verify(messageRepository, times(2)).save(messageCaptor.capture());
    }

    @DisplayName("2. 기존 세션에서 파일 없이 일반 대화 유지")
    @Test
    void 기존_세션_일반_대화_유지() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));
        given(messageRepository.findFilesBySessionIdAndStatus(SESSION_ID, FileStatus.COMPLETED))
            .willReturn(Collections.emptyList());
        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"답변\"}");

        var request = new ChatProxyRequest(SESSION_ID, "질문", 0L);
        chatService.processChat(USER_ID, request, Collections.emptyList());

        verify(ragChatService).relayChatWithImages(eq("/normal/chat"), any(FastApiChatRequest.class), anyList());
    }

    @DisplayName("3. 파일 등록 후 자동 RAG 모드 전환 (파일명 전송 검증)")
    @Test
    void 파일_등록_후_자동_RAG_모드_전환() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));
        given(uploadedFileRepository.findByIdAndUserAccountId(FILE_ID_A, USER_ID)).willReturn(Optional.of(mockFileA));

        given(messageRepository.findFilesBySessionIdAndStatus(SESSION_ID, FileStatus.COMPLETED))
            .willReturn(new java.util.ArrayList<>());

        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"RAG 답변\"}");

        var request = new ChatProxyRequest(SESSION_ID, "파일 질문", FILE_ID_A);
        chatService.processChat(USER_ID, request, Collections.emptyList());

        verify(ragChatService).relayChatWithImages(eq("/rag/chat"), fastApiRequestCaptor.capture(), anyList());
        assertThat(fastApiRequestCaptor.getValue().fileNames()).contains("file_A.pdf");
    }

    @DisplayName("4. 파일 등록 후 다음 턴에 자동 RAG 유지 (히스토리 기반)")
    @Test
    void 파일_등록_후_다음_턴_자동_RAG_유지() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));

        given(messageRepository.findFilesBySessionIdAndStatus(SESSION_ID, FileStatus.COMPLETED))
            .willReturn(List.of(mockFileA));

        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"RAG 답변\"}");

        var request = new ChatProxyRequest(SESSION_ID, "이전 내용 질문", 0L);
        chatService.processChat(USER_ID, request, Collections.emptyList());

        verify(ragChatService).relayChatWithImages(eq("/rag/chat"), fastApiRequestCaptor.capture(), anyList());
        assertThat(fastApiRequestCaptor.getValue().fileNames()).contains("file_A.pdf");
    }

    @DisplayName("5. 파일 상태가 COMPLETED가 아니면 예외 발생")
    @Test
    void 파일_상태_가드_예외_발생() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));

        UploadedFile pendingFile = UploadedFile.builder().id(FILE_ID_A).status(FileStatus.PENDING).build();
        given(uploadedFileRepository.findByIdAndUserAccountId(FILE_ID_A, USER_ID))
            .willReturn(Optional.of(pendingFile));

        var request = new ChatProxyRequest(SESSION_ID, "질문", FILE_ID_A);

        assertThatThrownBy(() -> chatService.processChat(USER_ID, request, Collections.emptyList()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not ready");
    }

    @DisplayName("6. 채팅 메시지 목록 조회 시 페이지네이션 확인")
    @Test
    void 채팅_메시지_목록_조회_페이지네이션() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));
        Pageable pageable = Pageable.ofSize(10);

        // [수정] Repository Mocking 추가 (빈 Slice 반환)
        Slice<ChatMessage> emptySlice = new SliceImpl<>(Collections.emptyList());
        given(messageRepository.findAllBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any(Pageable.class)))
                .willReturn(emptySlice);

        chatService.getMessagesBySession(USER_ID, SESSION_ID, pageable);

        verify(messageRepository).findAllBySessionIdOrderByCreatedAtDesc(SESSION_ID, pageable);
    }

    @DisplayName("7. 타인의 세션에 접근 시 AccessDeniedException 발생")
    @Test
    void 타인의_세션_접근_차단() {
        Long otherUserId = 2L;
        UserAccount otherUser = UserAccount.builder().id(otherUserId).build();
        ChatSession otherSession = ChatSession.builder().id(999L).userAccount(otherUser).build();

        given(sessionRepository.findById(999L)).willReturn(Optional.of(otherSession));

        var request = new ChatProxyRequest(999L, "훔쳐보기", 0L);

        assertThatThrownBy(() -> chatService.processChat(USER_ID, request, Collections.emptyList()))
            .isInstanceOf(AccessDeniedException.class);
    }
}