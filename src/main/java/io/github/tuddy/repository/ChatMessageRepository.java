package io.github.tuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.chat.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // session.id를 기준으로 모든 ChatMessage를 찾아 시간순(createdAt Asc)으로 정렬
    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(Long sessionId);
}