// 파일 위치: src/main/java/io/github/tuddy/service/FileService.java
package io.github.tuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tuddy.dto.UploadedFileResponse;
import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;

// 사용자가 업로드한 파일 목록을 조회하고, 파일을 삭제하는 비즈니스 로직
@Service
@RequiredArgsConstructor
public class FileService {

    private final UploadedFileRepository uploadedFileRepository;
    private final S3Service s3Service;

    // 현재 로그인한 사용자가 업로드한 모든 파일 목록을 조회
    @Transactional(readOnly = true)
    public List<UploadedFileResponse> getMyFiles(Long userId) {
        return uploadedFileRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(UploadedFileResponse::from)
                .collect(Collectors.toList());
    }

    // 파일 삭제 : DB와 S3에서 모두 삭제됨
    @Transactional
    public void deleteFile(Long userId, Long fileId) {
        // 1. 본인 소유의 파일이 맞는지 확인하며 파일 정보를 조회
        UploadedFile file = uploadedFileRepository.findByIdAndUserAccountId(fileId, userId)
                .orElseThrow(() -> new AccessDeniedException("File not found or you don't have permission."));

        // 2. S3에서 실제 파일을 삭제
        s3Service.deleteFile(file.getS3Key());

        // 3. 데이터베이스에서 파일 정보를 삭제
        uploadedFileRepository.delete(file);
    }
}