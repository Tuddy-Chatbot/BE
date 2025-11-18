package io.github.tuddy.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.chat.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Pageable을 받아 Slice를 반환
    Slice<ChatMessage> findAllBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);
}