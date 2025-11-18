package io.github.tuddy.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.tuddy.entity.chat.ChatMessage;
import io.github.tuddy.entity.file.FileStatus;
import io.github.tuddy.entity.file.UploadedFile;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Pageable을 받아 Slice를 반환
    Slice<ChatMessage> findAllBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);


    // 특정 세션 내에서 참조된 모든 '처리 완료된' 파일을 중복 없이 조회
    @Query("SELECT DISTINCT m.uploadedFile FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId " +
           "AND m.uploadedFile IS NOT NULL " +
           "AND m.uploadedFile.status = :status")
    List<UploadedFile> findFilesBySessionIdAndStatus(@Param("sessionId") Long sessionId,
                                                     @Param("status") FileStatus status);
}