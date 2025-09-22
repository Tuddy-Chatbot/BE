package io.github.tuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.chat.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

	// userAccount.id를 기준으로 모든 ChatSession을 찾아 최신순(createdAt Desc)으로 정렬
	List<ChatSession> findAllByUserAccountIdOrderByCreatedAtDesc(Long userId);
}