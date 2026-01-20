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

        // 2. [수정] 파일 처리 로직 개선
        // Case A: 새로 업로드된 파일이 있는 경우
        if (files != null && !files.isEmpty()) {
            MultipartFile file = files.get(0);
            UploadedFileResponse uploadedDto = fileService.uploadFile(file, userId);
            currentFile = uploadedFileRepository.findById(uploadedDto.id())
                    .orElseThrow(() -> new IllegalStateException("File saved but not found"));
        }
        // Case B: 기존 파일을 선택해서 보낸 경우 (fileId가 있는 경우)
        else if (req.fileId() != null && req.fileId() != 0) {
            currentFile = uploadedFileRepository.findByIdAndUserAccountId(req.fileId(), userId)
                    .orElseThrow(() -> new AccessDeniedException("File not found or access denied"));
        }

        // 3. [트랜잭션] 사용자 메시지 저장 (파일 정보가 있으면 같이 저장됨)
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

        // 5. [핵심 수정] RAG 모드 라우팅 조건 변경
        // 새 파일이 있거나(files) OR 기존 파일을 선택했거나(currentFile) -> RAG
        if ((files != null && !files.isEmpty()) || currentFile != null) {
            log.info("Routing to RAG Chat (Multipart). Session: {}, FileId: {}", session.getId(),
                     (currentFile != null ? currentFile.getId() : "New File"));

            // 파일이 없어도(null) currentFile이 있으면 RAG 경로로 보냄 (FastAPI가 세션/벡터DB 참조)
            botAnswerJson = ragChatService.relayChatWithImages(
                    ragChatService.getChatPath(),
                    fastApiReq,
                    files
            );
        } else {
            // 파일 관련 내용이 전혀 없으면 일반 대화
            // (단, 이 세션의 과거 기록에 파일이 있었다면 RAG로 보내는 게 좋을 수 있음.
            //  필요 시 existsBySessionIdAndUploadedFileIsNotNull 체크 추가 가능)
            boolean hasHistory = messageRepository.existsBySessionIdAndUploadedFileIsNotNull(session.getId());

            if (hasHistory) {
                log.info("Routing to RAG Chat (History based). Session: {}", session.getId());
                botAnswerJson = ragChatService.relayChatWithImages(ragChatService.getChatPath(), fastApiReq, null);
            } else {
                log.info("Routing to Normal Chat (JSON). Session: {}", session.getId());
                botAnswerJson = ragChatService.relayNormal(fastApiReq);
            }
        }

        // 6. AI 응답 파싱
        String botAnswerText = parseAnswer(botAnswerJson);

        // 7. [트랜잭션] 봇 응답 저장
        transactionTemplate.executeWithoutResult(status ->
            saveMessage(session, SenderType.BOT, botAnswerText, null)
        );

        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    private ChatSession findOrCreateSession(Long userId, ChatProxyRequest req) {
        if (req.sessionId() != null && req.sessionId() != 0) {
            return sessionRepository.findById(req.sessionId())
                    .filter(s -> s.getUserAccount().getId().equals(userId))
                    .orElseThrow(() -> new AccessDeniedException("Session access denied"));
        }
        UserAccount user = userAccountRepository.getReferenceById(userId);
        String title = (req.query() != null && req.query().length() > 20)
                     ? req.query().substring(0, 20) : (req.query() == null ? "New Chat" : req.query());
        return sessionRepository.save(ChatSession.builder().userAccount(user).title(title).build());
    }

    private void saveMessage(ChatSession session, SenderType sender, String content, UploadedFile file) {
        messageRepository.save(ChatMessage.builder()
                .session(session).senderType(sender).content(content).uploadedFile(file).build());
    }

    private String parseAnswer(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
			return "응답 없음";
		}
        try {
            if (jsonResponse.trim().startsWith("{")) {
                FastApiResponse res = objectMapper.readValue(jsonResponse, FastApiResponse.class);
                return res.response() != null ? res.response() : "AI 응답 내용 없음";
            }
            return jsonResponse;
        } catch (JsonProcessingException e) {
            log.error("JSON Error", e);
            return jsonResponse;
        }
    }

    public List<ChatSessionResponse> getMyChatSessions(Long userId) {
        return sessionRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)
                .stream().map(ChatSessionResponse::from).collect(Collectors.toList());
    }

    public Slice<ChatMessageResponse> getMessagesBySession(Long userId, Long sessionId, Pageable pageable) {
        return messageRepository.findAllBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                .map(ChatMessageResponse::from);
    }
}