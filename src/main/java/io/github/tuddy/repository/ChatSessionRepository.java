package io.github.tuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.chat.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

	// 기존 메서드 대체: Pageable을 받아 Slice 반환 (무한 스크롤에 최적화)
	List<ChatSession> findAllByUserAccountIdOrderByCreatedAtDesc(Long userId);
}