package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.FastApiChatRequest;
import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.chat.ChatSession;
import io.github.tuddy.entity.chat.SenderType;
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
    private static final Long FILE_ID_B = 11L;

    @InjectMocks
    private ChatService chatService;

    @Mock private RagChatService ragChatService;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private UploadedFileRepository uploadedFileRepository;
    @Spy private ObjectMapper objectMapper; // 실제 ObjectMapper 사용

    // 값 검증을 위한 Captor 정의
    @Captor private ArgumentCaptor<ChatMessage> messageCaptor;
    @Captor private ArgumentCaptor<FastApiChatRequest> fastApiRequestCaptor;

    private UserAccount mockUser;
    private ChatSession mockSession;
    private UploadedFile mockFileA;
    private UploadedFile mockFileB;

    @BeforeEach
    void setUp() {
        mockUser = UserAccount.builder().id(USER_ID).build();
        mockSession = ChatSession.builder().id(SESSION_ID).userAccount(mockUser).build();

        mockFileA = UploadedFile.builder().id(FILE_ID_A).originalFilename("file_A.pdf").status(FileStatus.COMPLETED).build();
        mockFileB = UploadedFile.builder().id(FILE_ID_B).originalFilename("file_B.pdf").status(FileStatus.COMPLETED).build();

        // 1. 기본 ID 조회 Stubbing (모든 테스트 공통)
        // lenient()를 사용하여 불필요한 Stubbing 에러 방지 (테스트 케이스마다 호출 안될 수도 있음)
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.of(mockUser));

        // 2. 경로 조회 Stubbing
        // lenient() 사용: 일부 테스트(예: 상태 가드 테스트)에서는 호출되지 않을 수 있음
        org.mockito.Mockito.lenient().when(ragChatService.getChatPath()).thenReturn("/rag/chat");
        org.mockito.Mockito.lenient().when(ragChatService.getNormalPath()).thenReturn("/normal/chat");
    }

    @DisplayName("1. 신규 채팅 생성 및 일반 대화 (Normal Chat)")
    @Test
    void 신규_채팅_일반_대화() {
        // Given
        var request = new ChatProxyRequest(null, "새 질문", 0L);
        // 세션 저장 시 새로운 세션 객체 반환
        var newSession = ChatSession.builder().id(101L).userAccount(mockUser).title("새 질문").build();
        given(sessionRepository.save(any(ChatSession.class))).willReturn(newSession);

        // 메시지 파일 조회 시 빈 리스트 (초기 상태)
        given(messageRepository.findFilesBySessionIdAndStatus(101L, FileStatus.COMPLETED))
            .willReturn(Collections.emptyList());

        // FastAPI 응답 Mock
        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"일반 답변\"}");

        // When
        chatService.processChat(USER_ID, request, Collections.emptyList());

        // Then
        // 1. Normal Path 호출 확인
        verify(ragChatService).relayChatWithImages(eq("/normal/chat"), any(FastApiChatRequest.class), anyList());

        // 2. 메시지 저장 검증 (User 1회, Bot 1회)
        verify(messageRepository, times(2)).save(messageCaptor.capture());
        List<ChatMessage> savedMessages = messageCaptor.getAllValues();

        // User 메시지 검증
        assertThat(savedMessages.get(0).getSenderType()).isEqualTo(SenderType.USER);
        assertThat(savedMessages.get(0).getContent()).isEqualTo("새 질문");
        assertThat(savedMessages.get(0).getUploadedFile()).isNull(); // 파일 없음

        // Bot 메시지 검증
        assertThat(savedMessages.get(1).getSenderType()).isEqualTo(SenderType.BOT);
        assertThat(savedMessages.get(1).getContent()).isEqualTo("일반 답변");
    }

    @DisplayName("2. 기존 세션에서 파일 없이 일반 대화 유지")
    @Test
    void 기존_세션_일반_대화_유지() {
        // Given
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));
        // 기존 히스토리에도 파일 없음
        given(messageRepository.findFilesBySessionIdAndStatus(SESSION_ID, FileStatus.COMPLETED))
            .willReturn(Collections.emptyList());

        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"답변\"}");

        var request = new ChatProxyRequest(SESSION_ID, "질문", 0L);

        // When
        chatService.processChat(USER_ID, request, Collections.emptyList());

        // Then
        verify(ragChatService).relayChatWithImages(eq("/normal/chat"), any(FastApiChatRequest.class), anyList());
    }

    @DisplayName("3. 파일 등록 후 자동 RAG 모드 전환 (파일명 전송 검증)")
    @Test
    void 파일_등록_후_자동_RAG_모드_전환() {
        // Given
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));
        // 요청에 파일 ID 포함
        given(uploadedFileRepository.findByIdAndUserAccountId(FILE_ID_A, USER_ID))
            .willReturn(Optional.of(mockFileA));
        // 기존 히스토리에는 파일 없음 (이번이 처음)
        given(messageRepository.findFilesBySessionIdAndStatus(SESSION_ID, FileStatus.COMPLETED))
            .willReturn(new java.util.ArrayList<>());

        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"RAG 답변\"}");

        var request = new ChatProxyRequest(SESSION_ID, "파일 질문", FILE_ID_A);

        // When
        chatService.processChat(USER_ID, request, Collections.emptyList());

        // Then
        // 1. RAG Path 호출 확인
        verify(ragChatService).relayChatWithImages(eq("/rag/chat"), fastApiRequestCaptor.capture(), anyList());

        // 2. FastAPI로 전송된 요청에 파일명이 포함되었는지 확인
        FastApiChatRequest capturedRequest = fastApiRequestCaptor.getValue();
        assertThat(capturedRequest.fileNames()).contains("file_A.pdf");

        // 3. User 메시지에 파일이 연결되어 저장되었는지 확인
        verify(messageRepository, times(2)).save(messageCaptor.capture());
        ChatMessage userMessage = messageCaptor.getAllValues().get(0);
        assertThat(userMessage.getUploadedFile()).isNotNull();
        assertThat(userMessage.getUploadedFile().getId()).isEqualTo(FILE_ID_A);
    }

    @DisplayName("4. 파일 등록 후 다음 턴에 자동 RAG 유지 (히스토리 기반)")
    @Test
    void 파일_등록_후_다음_턴_자동_RAG_유지() {
        // Given
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));
        // 요청에는 파일 ID 없음 (일반 질문처럼 보임)
        var request = new ChatProxyRequest(SESSION_ID, "이전 내용 질문", 0L);

        // ★ 핵심: DB에서 히스토리 파일을 조회했더니 파일 A가 나옴
        given(messageRepository.findFilesBySessionIdAndStatus(SESSION_ID, FileStatus.COMPLETED))
            .willReturn(List.of(mockFileA));

        given(ragChatService.relayChatWithImages(anyString(), any(FastApiChatRequest.class), anyList()))
            .willReturn("{\"response\":\"RAG 답변\"}");

        // When
        chatService.processChat(USER_ID, request, Collections.emptyList());

        // Then
        // RAG Path로 갔는지 확인 (자동 RAG)
        verify(ragChatService).relayChatWithImages(eq("/rag/chat"), fastApiRequestCaptor.capture(), anyList());

        // 파일명이 전송되었는지 확인
        assertThat(fastApiRequestCaptor.getValue().fileNames()).contains("file_A.pdf");
    }

    @DisplayName("5. 파일 상태가 COMPLETED가 아니면 예외 발생")
    @Test
    void 파일_상태_가드_예외_발생() {
        // Given
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));

        // PENDING 상태의 파일 리턴
        UploadedFile pendingFile = UploadedFile.builder().id(FILE_ID_A).status(FileStatus.PENDING).build();
        given(uploadedFileRepository.findByIdAndUserAccountId(FILE_ID_A, USER_ID))
            .willReturn(Optional.of(pendingFile));

        var request = new ChatProxyRequest(SESSION_ID, "질문", FILE_ID_A);

        // When & Then
        assertThatThrownBy(() -> chatService.processChat(USER_ID, request, Collections.emptyList()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not ready");
    }

    @DisplayName("6. 채팅 메시지 목록 조회 시 페이지네이션 확인")
    @Test
    void 채팅_메시지_목록_조회_페이지네이션() {
        // Given
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(mockSession));
        Pageable pageable = Pageable.ofSize(10);

        // When
        chatService.getMessagesBySession(USER_ID, SESSION_ID, pageable);

        // Then
        verify(messageRepository).findAllBySessionIdOrderByCreatedAtDesc(SESSION_ID, pageable);
    }
}