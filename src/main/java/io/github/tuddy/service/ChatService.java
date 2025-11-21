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
        // 1. 세션 찾기 또는 생성 (파일 연결 로직 제거됨)
        ChatSession session = findOrCreateSession(userId, req);

        // 2. 신규 파일 처리 : 이번 요청에 명시적으로 연결된 파일이 있다면 확인 및 검증
        UploadedFile currentFile = null;
        if (req.fileId() != null && req.fileId() != 0) {
            currentFile = uploadedFileRepository.findByIdAndUserAccountId(req.fileId(), userId)
                    .orElseThrow(() -> new AccessDeniedException("File not found or permission denied."));

            // 처리 완료되지 않은 파일은 채팅에 사용할 수 없음
            if (currentFile.getStatus() != FileStatus.COMPLETED) {
                 throw new IllegalStateException("File is not ready. Status: " + currentFile.getStatus());
            }
        }

        // 3. 사용자 메시지 저장 (현재 파일이 있다면 메시지에 링크 - 히스토리용)
        saveMessage(session, SenderType.USER, req.query(), currentFile);

        // 4. [자동 RAG 컨텍스트 조회] : 이 세션에서 이전에 사용된 적 있는 모든 '완료된' 파일 조회
        List<UploadedFile> sessionFiles = messageRepository.findFilesBySessionIdAndStatus(
                session.getId(), FileStatus.COMPLETED);

        // 방금 추가한 파일(currentFile)이 DB 쿼리 시점에 아직 반영되지 않았을 수 있으므로 리스트에 추가
        if (currentFile != null && !sessionFiles.contains(currentFile)) {
            sessionFiles.add(currentFile);
        }

        // 5. FastAPI 요청 객체 생성 : 파일이 하나라도 있으면 파일명 리스트를 전달, 없으면 null
        List<String> targetFileNames = sessionFiles.isEmpty() ? null :
            sessionFiles.stream().map(UploadedFile::getOriginalFilename).collect(Collectors.toList());

        var fastApiReq = new FastApiChatRequest(
                String.valueOf(userId),
                String.valueOf(session.getId()),
                req.query(),
                N_TURNS,
                targetFileNames // 세션 내 모든 파일명 전달 (자동 RAG)
        );

        // 6. API 경로 결정 (파일 히스토리가 존재하면 무조건 RAG)
        String apiPath = (targetFileNames != null && !targetFileNames.isEmpty())
                ? ragChatService.getChatPath()
                : ragChatService.getNormalPath();

        log.info("Session {}: Using {} files for context. Mode: {}",
                 session.getId(), (targetFileNames != null ? targetFileNames.size() : 0), apiPath);

        // 7. FastAPI 호출 (이미지가 있으면 함께 전송)
        String botAnswerJson = ragChatService.relayChatWithImages(apiPath, fastApiReq, files);

        // 8. 응답 저장
        String botAnswerText = parseAnswer(botAnswerJson);
        saveMessage(session, SenderType.BOT, botAnswerText, null); // 봇은 파일 참조 없음

        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    private ChatSession findOrCreateSession(Long userId, ChatProxyRequest req) {
        // 1. 기존 세션 조회
        if (req.sessionId() != null && req.sessionId() != 0) {
            return sessionRepository.findById(req.sessionId())
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        }

        // 2. 새로운 세션 생성 (파일 연결 로직 완전히 제거 - 순수 대화방)
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String title = req.query().length() > 50 ? req.query().substring(0, 50) : req.query();

        ChatSession newSession = ChatSession.builder()
                .userAccount(user)
                .title(title)
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

    // 파일 참조 정보를 포함하는 저장 메서드 (오버로딩)
    private void saveMessage(ChatSession session, SenderType sender, String content, UploadedFile file) {
        ChatMessage message = ChatMessage.builder()
            .session(session)
            .senderType(sender)
            .content(content)
            .uploadedFile(file) // 메시지에 파일 연결
            .build();
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

        // 최신순으로 페이징 조회
        return messageRepository.findAllBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                .map(ChatMessageResponse::from);
    }
}