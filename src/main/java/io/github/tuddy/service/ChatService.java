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
    private final UserAccountRepository userAccountRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final ObjectMapper objectMapper;

    private static final int N_TURNS = 7;

    @Transactional
    public ChatProxyResponse processChat(Long userId, ChatProxyRequest req, List<MultipartFile> files) {
        // 1. 세션 찾기 또는 생성
        ChatSession session = findOrCreateSession(userId, req);

        // 2. 신규 파일 처리
        // [수정] record 타입이므로 getFileId() -> fileId()
        UploadedFile currentFile = null;
        if (req.fileId() != null && req.fileId() != 0) {
            currentFile = uploadedFileRepository.findByIdAndUserAccountId(req.fileId(), userId)
                    .orElseThrow(() -> new AccessDeniedException("File not found or permission denied."));

            if (currentFile.getStatus() != FileStatus.COMPLETED) {
                 throw new IllegalStateException("File is not ready. Status: " + currentFile.getStatus());
            }
        }

        // 3. 사용자 메시지 저장
        // [수정] getQuery() -> query()
        saveMessage(session, SenderType.USER, req.query(), currentFile);

        // 4. [자동 RAG] 세션 내 완료된 파일 조회
        List<UploadedFile> sessionFiles = messageRepository.findFilesBySessionIdAndStatus(
                session.getId(), FileStatus.COMPLETED);

        if (currentFile != null && !sessionFiles.contains(currentFile)) {
            sessionFiles.add(currentFile);
        }

        // 5. FastAPI 요청 생성
        List<String> targetFileNames = sessionFiles.isEmpty() ? null :
            sessionFiles.stream().map(UploadedFile::getOriginalFilename).collect(Collectors.toList());

        var fastApiReq = new FastApiChatRequest(
                String.valueOf(userId),
                String.valueOf(session.getId()),
                req.query(), // [수정] query()
                N_TURNS,
                targetFileNames
        );

        // 6. 모드 결정
        String apiPath = (targetFileNames != null && !targetFileNames.isEmpty())
                ? ragChatService.getChatPath()
                : ragChatService.getNormalPath();

        log.info("Session {}: Using {} files. Mode: {}",
                 session.getId(), (targetFileNames != null ? targetFileNames.size() : 0), apiPath);

        // 7. FastAPI 호출
        String botAnswerJson = ragChatService.relayChatWithImages(apiPath, fastApiReq, files);

        // 8. 응답 처리 및 저장
        String botAnswerText = parseAnswer(botAnswerJson);
        saveMessage(session, SenderType.BOT, botAnswerText, null);

        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    private ChatSession findOrCreateSession(Long userId, ChatProxyRequest req) {
        // [수정] getSessionId() -> sessionId()
        if (req.sessionId() != null && req.sessionId() != 0) {
            ChatSession session = sessionRepository.findById(req.sessionId())
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));

            if (!session.getUserAccount().getId().equals(userId)) {
                throw new AccessDeniedException("Access denied");
            }
            return session;
        }

        UserAccount user = userAccountRepository.getReferenceById(userId);

        // [수정] getQuery() -> query()
        String query = req.query();
        String title = (query != null && query.length() > 50) ? query.substring(0, 50) : query;
        if (title == null || title.isBlank()) {
			title = "New Chat";
		}

        return sessionRepository.save(ChatSession.builder()
                .userAccount(user)
                .title(title)
                .build());
    }

    // ... (이하 parseAnswer, saveMessage 등은 그대로 사용) ...

    private String parseAnswer(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
			return "답변 없음";
		}
        try {
            FastApiResponse res = objectMapper.readValue(jsonResponse, FastApiResponse.class);
            // record라면 res.response(), class라면 res.getResponse() 확인 필요 (FastApiResponse도 record로 추정됨)
            return res.response() != null ? res.response() : "생성 실패";
        } catch (JsonProcessingException e) {
            log.error("JSON Parse Error", e);
            return "오류: " + jsonResponse;
        }
    }

    private void saveMessage(ChatSession session, SenderType sender, String content, UploadedFile file) {
        messageRepository.save(ChatMessage.builder()
            .session(session)
            .senderType(sender)
            .content(content)
            .uploadedFile(file)
            .build());
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getMyChatSessions(Long userId) {
        return sessionRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ChatSessionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Slice<ChatMessageResponse> getMessagesBySession(Long userId, Long sessionId, Pageable pageable) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUserAccount().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        return messageRepository.findAllBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                .map(ChatMessageResponse::from);
    }
}