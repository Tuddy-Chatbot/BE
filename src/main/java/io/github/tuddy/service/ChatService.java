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
        // 1. 세션 생성 및 사용자 메시지 저장 (기존과 동일)
        ChatSession session = findOrCreateSession(userId, req);
        saveMessage(session, SenderType.USER, req.query());

        var fastApiReq = new FastApiChatRequest(String.valueOf(userId), String.valueOf(session.getId()), req.query(), N_TURNS);

        // 2. RAG 여부에 따라 API 경로 결정 (기존과 동일)
        String apiPath = (session.getUploadedFile() != null) ? ragChatService.getChatPath() : ragChatService.getNormalPath();

        // 3. (로직 수정) 이미지가 있든 없든 항상 relayChatWithImages 메서드를 호출
        log.info("Calling FastAPI chat for session ID: {}. Image count: {}",
                 session.getId(), (files != null ? files.size() : 0));
        String botAnswerJson = ragChatService.relayChatWithImages(apiPath, fastApiReq, files);

        // 4. 응답 처리 및 저장 (기존과 동일)
        String botAnswerText = parseAnswer(botAnswerJson);
        saveMessage(session, SenderType.BOT, botAnswerText);
        return new ChatProxyResponse(session.getId(), botAnswerText);
    }


    private ChatSession findOrCreateSession(Long userId, ChatProxyRequest req) {
        // 1. 기존 세션을 이어가는 경우
        if (req.sessionId() != null && req.sessionId() != 0) {
            ChatSession session = sessionRepository.findById(req.sessionId())
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));

            // 2. 만약 이 세션에 파일이 없는데, 이번 요청에 유효한 fileId가 있다면 파일을 연결
            // fileId가 0인 경우를 무시하도록 조건을 추가
            if (session.getUploadedFile() == null && req.fileId() != null && req.fileId() != 0) {
                UploadedFile uploadedFile = uploadedFileRepository.findByIdAndUserAccountId(req.fileId(), userId)
                    .orElseThrow(() -> new AccessDeniedException("File not found or you don't have permission."));
                session.setUploadedFile(uploadedFile);
            }
            return session;
        }

        // 3. 새로운 세션을 시작하는 경우
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UploadedFile uploadedFile = null;
        // fileId가 0인 경우를 무시하도록 조건을 추가
        if (req.fileId() != null && req.fileId() != 0) {
            uploadedFile = uploadedFileRepository.findByIdAndUserAccountId(req.fileId(), userId)
                .orElseThrow(() -> new AccessDeniedException("File not found or you don't have permission."));
        }

        String title = req.query().length() > 50 ? req.query().substring(0, 50) : req.query();
        ChatSession newSession = ChatSession.builder()
                .userAccount(user)
                .title(title)
                .uploadedFile(uploadedFile)
                .build();
        return sessionRepository.save(newSession);
    }

    private String parseAnswer(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
			return "답변을 받지 못했습니다.";
		}
        try {
            FastApiResponse response = objectMapper.readValue(jsonResponse, FastApiResponse.class);
            if (response.response() == null || response.response().isBlank()) {
                return "답변을 생성하지 못했습니다.";
            }
            return response.response();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse FastAPI response: {}", jsonResponse, e);
            return "챗봇 응답 처리 중 오류가 발생했습니다.";
        }
    }

    private void saveMessage(ChatSession session, SenderType sender, String content) {
        ChatMessage message = ChatMessage.builder()
            .session(session).senderType(sender).content(content).build();
        messageRepository.save(message);
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
            throw new AccessDeniedException("You do not have permission to access this chat session.");
        }

        // ★ 수정됨: Repository와 동일하게 Desc 호출
        return messageRepository.findAllBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                .map(ChatMessageResponse::from);
    }
}