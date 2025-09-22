package io.github.tuddy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.file.UploadedFile;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    // 현재 로그인한 사용자가 업로드한 모든 파일을 최신순으로 조회
    List<UploadedFile> findAllByUserAccountIdOrderByCreatedAtDesc(Long userId);

    // 파일 ID와 사용자 ID를 함께 사용하여 본인 소유의 파일이 맞는지 확인
    Optional<UploadedFile> findByIdAndUserAccountId(Long id, Long userId);
}