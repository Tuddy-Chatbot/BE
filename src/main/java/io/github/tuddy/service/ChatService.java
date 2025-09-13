package io.github.tuddy.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.FastApiChatRequest;
import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.chat.ChatSession;
import io.github.tuddy.entity.chat.SenderType;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.ChatMessageRepository;
import io.github.tuddy.repository.ChatSessionRepository;
import io.github.tuddy.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RagChatService ragChatService;
    private final UserAccountRepository userAccountRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private static final int N_TURNS = 7; // 고정값

    @Transactional
    public ChatProxyResponse processRagChat(Long userId, ChatProxyRequest req) {
        // 1. 세션 조회 또는 생성
        ChatSession session = findOrCreateSession(userId, req.sessionId(), req.query());

        // 2. 사용자 메시지 DB에 저장
        saveMessage(session, SenderType.USER, req.query());

        // 3. FastAPI에 보낼 요청 생성
        var fastApiReq = new FastApiChatRequest(
            String.valueOf(userId),
            String.valueOf(session.getId()),
            req.query(),
            N_TURNS
        );

        // 4. FastAPI로 요청 릴레이
        String botAnswerJson = ragChatService.relayRag(fastApiReq);
        // TODO: 실제 서비스에서는 JSON을 파싱하여 순수 텍스트 답변만 추출해야 합니다.
        // 예: ObjectMapper mapper = new ObjectMapper(); String answerText = mapper.readTree(botAnswerJson).get("answer").asText();
        String botAnswerText = botAnswerJson; // 지금은 전체 JSON을 그대로 저장

        // 5. 봇 답변 DB에 저장
        saveMessage(session, SenderType.BOT, botAnswerText);

        // 6. 프론트엔드에 응답 반환
        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    // processNormalChat 메서드도 위와 동일한 구조로 만듭니다.
    // ragChatService.relayNormal(fastApiReq)를 호출하는 부분만 다릅니다.
    @Transactional
    public ChatProxyResponse processNormalChat(Long userId, ChatProxyRequest req) {
        ChatSession session = findOrCreateSession(userId, req.sessionId(), req.query());
        saveMessage(session, SenderType.USER, req.query());
        var fastApiReq = new FastApiChatRequest(String.valueOf(userId), String.valueOf(session.getId()), req.query(), N_TURNS);
        String botAnswerJson = ragChatService.relayNormal(fastApiReq);
        String botAnswerText = botAnswerJson;
        saveMessage(session, SenderType.BOT, botAnswerText);
        return new ChatProxyResponse(session.getId(), botAnswerText);
    }

    private ChatSession findOrCreateSession(Long userId, Long sessionId, String query) {
        // sessionId가 없으면 새로운 채팅 -> 세션을 새로 만든다.
        if (sessionId == null || sessionId == 0) {
            UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String title = query.length() > 50 ? query.substring(0, 50) : query;

            ChatSession newSession = ChatSession.builder()
                .userAccount(user)
                .title(title)
                .build();
            return sessionRepository.save(newSession);
        }
        // sessionId가 있으면 기존 채팅 -> ID로 조회해서 반환한다.
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private void saveMessage(ChatSession session, SenderType sender, String content) {
        ChatMessage message = ChatMessage.builder()
            .session(session)
            .senderType(sender)
            .content(content)
            .build();
        messageRepository.save(message);
    }
}