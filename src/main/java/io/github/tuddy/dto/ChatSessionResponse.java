package io.github.tuddy.dto;

import java.time.LocalDateTime;

import io.github.tuddy.entity.chat.ChatSession;

// 사용자의 전체 대화 목록에 포함될 개별 대화방의 정보
public record ChatSessionResponse(
    Long id,
    String title,
    LocalDateTime createdAt
) {
    // 엔티티를 DTO로 변환하는 정적 팩토리 메서드
    public static ChatSessionResponse from(ChatSession chatSession) {
        return new ChatSessionResponse(
            chatSession.getId(),
            chatSession.getTitle(),
            chatSession.getCreatedAt()
        );
    }
}