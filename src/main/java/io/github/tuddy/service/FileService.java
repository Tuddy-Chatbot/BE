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
    private final RagChatService ragChatService; // AI 연결용

    /**
     * 채팅용 파일 처리 (S3 업로드 -> DB 저장 -> AI 인덱싱 요청)
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
                .status(FileStatus.PROCESSING)
                .build();

        UploadedFile savedFile = uploadedFileRepository.save(uploadedFile);

        // 3. AI 서버에 인덱싱(OCR) 요청
        try {
            ragChatService.sendOcrRequest(String.valueOf(userId), s3Key);

            // 성공 시 상태 완료
            savedFile.setStatus(FileStatus.COMPLETED);
            uploadedFileRepository.save(savedFile);
            log.info("File indexed successfully: {}", s3Key);

        } catch (Exception e) {
            log.error("AI Indexing failed: {}", s3Key, e);
            // 인덱싱 실패해도 일단 채팅은 진행되도록 COMPLETED 처리 : 추후 변경 논의
            savedFile.setStatus(FileStatus.COMPLETED);
            uploadedFileRepository.save(savedFile);
        }

        return UploadedFileResponse.from(savedFile);
    }

    @Transactional
    public UploadedFile createFileMetadata(Long userId, String filename, String s3Key) {
        UploadedFile file = UploadedFile.builder()
                .userAccount(UserAccount.builder().id(userId).build())
                .originalFilename(filename)
                .s3Key(s3Key)
                .status(FileStatus.PENDING)
                .build();
        return uploadedFileRepository.save(file);
    }

    @Transactional
    public void processUploadedFile(Long userId, Long fileId) {
        UploadedFile file = uploadedFileRepository.findByIdAndUserAccountId(fileId, userId)
                .orElseThrow(() -> new AccessDeniedException("File not found"));

        file.setStatus(FileStatus.PROCESSING);
        uploadedFileRepository.saveAndFlush(file);

        try {
            ragChatService.sendOcrRequest(String.valueOf(userId), file.getS3Key());
            file.setStatus(FileStatus.COMPLETED);
        } catch (Exception e) {
            file.setStatus(FileStatus.FAILED);
            log.error("File processing failed", e);
        }
    }

    @Transactional(readOnly = true)
    public List<UploadedFileResponse> getMyFiles(Long userId) {
        return uploadedFileRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)
                .stream().map(UploadedFileResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public void deleteFile(Long userId, Long fileId) {
        UploadedFile file = uploadedFileRepository.findByIdAndUserAccountId(fileId, userId)
                .orElseThrow(() -> new AccessDeniedException("File not found"));
        s3Service.deleteFile(file.getS3Key());
        uploadedFileRepository.delete(file);
    }
}