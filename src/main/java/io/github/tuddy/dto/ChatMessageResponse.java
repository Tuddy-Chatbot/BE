package io.github.tuddy.dto;

import java.time.LocalDateTime;

import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.chat.SenderType;

// 특정 대화방을 클릭했을 때 보게 될 개별 메시지들의 정보
public record ChatMessageResponse(
    Long id,
    SenderType senderType,
    String content,
    LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage chatMessage) {
        return new ChatMessageResponse(
            chatMessage.getId(),
            chatMessage.getSenderType(),
            chatMessage.getContent(),
            chatMessage.getCreatedAt()
        );
    }
}