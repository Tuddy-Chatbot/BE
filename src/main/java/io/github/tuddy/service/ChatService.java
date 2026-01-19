package io.github.tuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final FileService fileService;
    private final UserAccountRepository userAccountRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    private static final int N_TURNS = 7;

    public ChatProxyResponse processChat(Long userId, ChatProxyRequest req, List<MultipartFile> files) {

        // 1. [트랜잭션] 세션 조회 또는 생성
        ChatSession session = transactionTemplate.execute(status -> findOrCreateSession(userId, req));

        UploadedFile currentFile = null;

        // 2. 파일이 있다면 업로드 처리 (S3 + DB + OCR 요청)
        // fileService.uploadFile 내부에서 OCR 요청까지 수행함
        if (files != null && !files.isEmpty()) {
            MultipartFile file = files.get(0);
            UploadedFileResponse uploadedDto = fileService.uploadFile(file, userId);

            // 저장된 파일 엔티티 조회
            currentFile = uploadedFileRepository.findById(uploadedDto.id())
                    .orElseThrow(() -> new IllegalStateException("File saved but not found"));
        }

        // 3. [트랜잭션] 사용자 메시지 DB 저장 (AI 요청 전에 저장하여 데이터 보존)
        final UploadedFile fileForSave = currentFile;
        transactionTemplate.executeWithoutResult(status ->
            saveMessage(session, SenderType.USER, req.query(), fileForSave)
        );

        // 4. AI 서버 요청 DTO 생성
        var fastApiReq = new FastApiChatRequest(
                String.valueOf(userId),
                String.valueOf(session.getId()),
                req.query(),
                N_TURNS
        );

        String botAnswerJson;

        // 5. [핵심 로직] 파일 유무에 따른 명확한 라우팅 분기
        if (files != null && !files.isEmpty()) {
            // Case A: 파일이 포함된 요청 -> /rag/chat (Multipart 전송)
            log.info("Routing to RAG Chat (Multipart) - File attached. Session: {}", session.getId());
            botAnswerJson = ragChatService.relayChatWithImages(
                    ragChatService.getChatPath(),
                    fastApiReq,
                    files
            );
        } else {
            // Case B: 파일이 없는 요청 -> /normal/chat (JSON 전송)
            log.info("Routing to Normal Chat (JSON) - No file. Session: {}", session.getId());
            botAnswerJson = ragChatService.relayNormal(fastApiReq);
        }

        // 6. AI 응답 파싱 (JSON -> String)
        String botAnswerText = parseAnswer(botAnswerJson);

        // 7. [트랜잭션] 봇 응답 DB 저장
        transactionTemplate.executeWithoutResult(status ->
            saveMessage(session, SenderType.BOT, botAnswerText, null)
        );

        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    // 세션 찾기 또는 생성
    private ChatSession findOrCreateSession(Long userId, ChatProxyRequest req) {
        if (req.sessionId() != null && req.sessionId() != 0) {
            return sessionRepository.findById(req.sessionId())
                    .filter(s -> s.getUserAccount().getId().equals(userId))
                    .orElseThrow(() -> new AccessDeniedException("Session access denied"));
        }
        UserAccount user = userAccountRepository.getReferenceById(userId);
        // 첫 메시지로 방 제목 생성 (최대 20자)
        String title = (req.query() != null && req.query().length() > 20)
                     ? req.query().substring(0, 20) : (req.query() == null ? "New Chat" : req.query());

        return sessionRepository.save(ChatSession.builder().userAccount(user).title(title).build());
    }

    // 메시지 저장
    private void saveMessage(ChatSession session, SenderType sender, String content, UploadedFile file) {
        messageRepository.save(ChatMessage.builder()
                .session(session)
                .senderType(sender)
                .content(content)
                .uploadedFile(file)
                .build());
    }

    // AI 응답 JSON 파싱
    private String parseAnswer(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
			return "응답 없음";
		}
        try {
            // 정상적인 JSON 응답인지 확인
            if (jsonResponse.trim().startsWith("{")) {
                FastApiResponse res = objectMapper.readValue(jsonResponse, FastApiResponse.class);
                return res.response() != null ? res.response() : "AI 응답 내용 없음";
            }
            // JSON이 아니면 에러 메시지일 가능성이 높음
            return jsonResponse;
        } catch (JsonProcessingException e) {
            log.error("JSON Parse Error: {}", jsonResponse, e);
            return "응답 처리 오류";
        }
    }

    // 세션 목록 조회
    public List<ChatSessionResponse> getMyChatSessions(Long userId) {
        return sessionRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)
                .stream().map(ChatSessionResponse::from).collect(Collectors.toList());
    }

    // 메시지 목록 조회
    public Slice<ChatMessageResponse> getMessagesBySession(Long userId, Long sessionId, Pageable pageable) {
        return messageRepository.findAllBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                .map(ChatMessageResponse::from);
    }
}