package io.github.tuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.github.tuddy.dto.UploadedFileResponse;
import io.github.tuddy.entity.file.FileStatus;
import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final UploadedFileRepository uploadedFileRepository;
    private final S3Service s3Service;
    private final RagChatService ragChatService;

    /**
     * [New] 서버 직접 업로드 방식 (ChatService에서 사용)
     */
    @Transactional
    public UploadedFileResponse uploadFile(MultipartFile file, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        // 1. S3 업로드
        String s3Key = s3Service.upload(file);

        // 2. DB 저장
        UploadedFile uploadedFile = UploadedFile.builder()
                .userAccount(UserAccount.builder().id(userId).build())
                .originalFilename(file.getOriginalFilename())
                .s3Key(s3Key)
                .status(FileStatus.COMPLETED)
                .build();

        UploadedFile savedFile = uploadedFileRepository.save(uploadedFile);
        return UploadedFileResponse.from(savedFile);
    }

    /**
     * [Legacy] Presigned URL 방식 메타데이터 생성 (UploadController에서 사용)
     * - createFileMetadata 메서드 복구
     */
    @Transactional
    public UploadedFile createFileMetadata(Long userId, String filename, String s3Key) {
        UploadedFile file = UploadedFile.builder()
                .userAccount(UserAccount.builder().id(userId).build())
                .originalFilename(filename)
                .s3Key(s3Key)
                .status(FileStatus.PENDING) // 업로드 대기 상태
                .build();
        return uploadedFileRepository.save(file);
    }

    /**
     * [Legacy] 업로드 완료 후 후처리 (FileController에서 사용)
     * - processUploadedFile 메서드 복구
     */
    @Transactional
    public void processUploadedFile(Long userId, Long fileId) {
        UploadedFile file = uploadedFileRepository.findByIdAndUserAccountId(fileId, userId)
                .orElseThrow(() -> new AccessDeniedException("File not found or access denied"));

        file.setStatus(FileStatus.PROCESSING);
        uploadedFileRepository.saveAndFlush(file);

        try {
            // OCR 요청 등 후처리 로직 (필요 시)
            // 현재는 바로 완료 처리
            file.setStatus(FileStatus.COMPLETED);
        } catch (Exception e) {
            file.setStatus(FileStatus.FAILED);
            log.error("File processing failed", e);
        }
    }

    @Transactional(readOnly = true)
    public List<UploadedFileResponse> getMyFiles(Long userId) {
        return uploadedFileRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(UploadedFileResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteFile(Long userId, Long fileId) {
        UploadedFile file = uploadedFileRepository.findByIdAndUserAccountId(fileId, userId)
                .orElseThrow(() -> new AccessDeniedException("File not found"));

        s3Service.deleteFile(file.getS3Key());
        uploadedFileRepository.delete(file);
    }
}