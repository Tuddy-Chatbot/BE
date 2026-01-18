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

    Slice<ChatMessage> findAllBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    @Query("SELECT DISTINCT m.uploadedFile FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId " +
           "AND m.uploadedFile IS NOT NULL " +
           "AND m.uploadedFile.status = :status")
    List<UploadedFile> findFilesBySessionIdAndStatus(@Param("sessionId") Long sessionId,
                                                     @Param("status") FileStatus status);
}