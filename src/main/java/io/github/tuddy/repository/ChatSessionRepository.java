package io.github.tuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.chat.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}