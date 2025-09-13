package io.github.tuddy.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.FastApiChatRequest;
import io.github.tuddy.dto.FastApiResponse; // 1단계에서 생성한 통합 DTO
import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.chat.ChatSession;
import io.github.tuddy.entity.chat.SenderType;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.ChatMessageRepository;
import io.github.tuddy.repository.ChatSessionRepository;
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
    private final ObjectMapper objectMapper;
    private static final int N_TURNS = 7;

    @Transactional
    public ChatProxyResponse processRagChat(Long userId, ChatProxyRequest req) {
        ChatSession session = findOrCreateSession(userId, req.sessionId(), req.query());
        saveMessage(session, SenderType.USER, req.query());
        var fastApiReq = new FastApiChatRequest(String.valueOf(userId), String.valueOf(session.getId()), req.query(), N_TURNS);

        String botAnswerJson = ragChatService.relayRag(fastApiReq);
        String botAnswerText = parseAnswer(botAnswerJson);

        saveMessage(session, SenderType.BOT, botAnswerText);
        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    @Transactional
    public ChatProxyResponse processNormalChat(Long userId, ChatProxyRequest req) {
        ChatSession session = findOrCreateSession(userId, req.sessionId(), req.query());
        saveMessage(session, SenderType.USER, req.query());
        var fastApiReq = new FastApiChatRequest(String.valueOf(userId), String.valueOf(session.getId()), req.query(), N_TURNS);

        String botAnswerJson = ragChatService.relayNormal(fastApiReq);
        String botAnswerText = parseAnswer(botAnswerJson);
        saveMessage(session, SenderType.BOT, botAnswerText);
        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    /**
     * FastAPI 응답 JSON 객체에서 'response' 필드의 값을 추출하는 통합 메서드
     */
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

    private ChatSession findOrCreateSession(Long userId, Long sessionId, String query) {
        if (sessionId == null || sessionId == 0) {
            UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            String title = query.length() > 50 ? query.substring(0, 50) : query;
            ChatSession newSession = ChatSession.builder()
                .userAccount(user).title(title).build();
            return sessionRepository.save(newSession);
        }
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private void saveMessage(ChatSession session, SenderType sender, String content) {
        ChatMessage message = ChatMessage.builder()
            .session(session).senderType(sender).content(content).build();
        messageRepository.save(message);
    }
}