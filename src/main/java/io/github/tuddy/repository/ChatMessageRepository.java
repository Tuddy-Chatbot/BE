package io.github.tuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.chat.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
}