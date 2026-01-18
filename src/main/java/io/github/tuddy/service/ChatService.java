package io.github.tuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatMessageResponse;
import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.ChatSessionResponse;
import io.github.tuddy.dto.FastApiChatRequest;
import io.github.tuddy.dto.FastApiResponse;
import io.github.tuddy.dto.UploadedFileResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RagChatService ragChatService;
    private final FileService fileService; // Step 2에서 수정한 서비스 주입
    private final UserAccountRepository userAccountRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final ObjectMapper objectMapper;

    private static final int N_TURNS = 7;

    /**
     * 메인 채팅 처리 로직
     * 주의: 전체 @Transactional을 걸지 않습니다. (DB 커넥션 점유 방지)
     * 필요한 구간(save 등)은 Repository가 트랜잭션을 처리하거나 내부 메서드에서 처리합니다.
     */
    public ChatProxyResponse processChat(Long userId, ChatProxyRequest req, List<MultipartFile> files) {

        // 1. 세션 조회/생성 (트랜잭션)
        ChatSession session = findOrCreateSession(userId, req);

        UploadedFile currentFile = null;

        // 2. 파일 저장 (S3 + DB)
        // 이 부분은 FileService.uploadFile 내부에서 별도 트랜잭션으로 처리됨
        if (files != null && !files.isEmpty()) {
            // 첫 번째 파일만 처리
            MultipartFile file = files.get(0);
            UploadedFileResponse uploadedDto = fileService.uploadFile(file, userId);

            // DTO -> Entity 변환 (또는 재조회)
            currentFile = uploadedFileRepository.findById(uploadedDto.id())
                    .orElseThrow(() -> new IllegalStateException("File saved but not found"));

        } else if (req.fileId() != null && req.fileId() != 0) {
            currentFile = uploadedFileRepository.findByIdAndUserAccountId(req.fileId(), userId)
                    .orElseThrow(() -> new AccessDeniedException("File not found"));
        }

        // 3. 사용자 메시지 저장 (트랜잭션)
        saveMessage(session, SenderType.USER, req.query(), currentFile);

        // 4. RAG 컨텍스트 파일 확인 (단순 조회)
        List<UploadedFile> sessionFiles = messageRepository.findFilesBySessionIdAndStatus(
                session.getId(), FileStatus.COMPLETED);

        // 방금 올린 파일 포함 여부 체크
        if (currentFile != null && !sessionFiles.contains(currentFile)) {
            sessionFiles.add(currentFile);
        }

        // 5. AI 요청 준비
        var fastApiReq = new FastApiChatRequest(
                String.valueOf(userId),
                String.valueOf(session.getId()),
                req.query(),
                N_TURNS
        );

        // 6. 모드 결정 및 AI 호출 (No Transaction, Blocking I/O)
        String apiPath = !sessionFiles.isEmpty() ? ragChatService.getChatPath() : ragChatService.getNormalPath();
        String botAnswerJson = ragChatService.relayChatWithImages(apiPath, fastApiReq, files);

        // 7. 봇 응답 저장 (트랜잭션)
        String botAnswerText = parseAnswer(botAnswerJson);
        saveMessage(session, SenderType.BOT, botAnswerText, null);

        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    // Helper: 세션 생성/조회 (트랜잭션 필요)
    @Transactional
    protected ChatSession findOrCreateSession(Long userId, ChatProxyRequest req) {
        if (req.sessionId() != null && req.sessionId() != 0) {
            return sessionRepository.findById(req.sessionId())
                    .filter(s -> s.getUserAccount().getId().equals(userId))
                    .orElseThrow(() -> new AccessDeniedException("Session access denied"));
        }
        UserAccount user = userAccountRepository.getReferenceById(userId);
        String title = (req.query() != null && req.query().length() > 20)
                     ? req.query().substring(0, 20) : (req.query() == null ? "New Chat" : req.query());

        return sessionRepository.save(ChatSession.builder()
                .userAccount(user)
                .title(title)
                .build());
    }

    // Helper: 메시지 저장 (트랜잭션 필요)
    @Transactional
    protected void saveMessage(ChatSession session, SenderType sender, String content, UploadedFile file) {
        messageRepository.save(ChatMessage.builder()
                .session(session)
                .senderType(sender)
                .content(content)
                .uploadedFile(file)
                .build());
    }

    private String parseAnswer(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
			return "답변 없음";
		}
        try {
            FastApiResponse res = objectMapper.readValue(jsonResponse, FastApiResponse.class);
            return res.response() != null ? res.response() : "응답 없음";
        } catch (JsonProcessingException e) {
            log.error("JSON Error", e);
            return "오류 발생";
        }
    }

    // 조회 로직들은 기존 유지...
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getMyChatSessions(Long userId) {
        return sessionRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)
                .stream().map(ChatSessionResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Slice<ChatMessageResponse> getMessagesBySession(Long userId, Long sessionId, Pageable pageable) {
        return messageRepository.findAllBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                .map(ChatMessageResponse::from);
    }
}