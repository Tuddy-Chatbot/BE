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
    private final RagChatService ragChatService; // AI 서버 통신용

    /**
     * 채팅용 파일 처리 (S3 저장 + DB 저장 + AI 인덱싱 요청)
     */
    @Transactional
    public UploadedFileResponse uploadFile(MultipartFile file, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        // 1. S3 업로드
        String s3Key = s3Service.upload(file);

        // 2. DB 저장 (초기 상태: PROCESSING)
        UploadedFile uploadedFile = UploadedFile.builder()
                .userAccount(UserAccount.builder().id(userId).build())
                .originalFilename(file.getOriginalFilename())
                .s3Key(s3Key)
                .status(FileStatus.PROCESSING)
                .build();

        UploadedFile savedFile = uploadedFileRepository.save(uploadedFile);

        // 3. AI 서버에 OCR 및 벡터 DB 저장 요청
        // (이게 있어야 나중에 RAG로 검색이 됩니다)
        try {
            ragChatService.sendOcrRequest(String.valueOf(userId), s3Key);

            // 성공 시 상태 완료로 변경
            savedFile.setStatus(FileStatus.COMPLETED);
            uploadedFileRepository.save(savedFile);
            log.info("File processed and indexed successfully: {}", s3Key);

        } catch (Exception e) {
            log.error("Failed to index file in AI server: {}", s3Key, e);
            // AI 쪽 인덱싱이 실패했더라도, 현재 채팅 턴에서는 파일을 직접 보내므로
            // 우선 COMPLETED로 처리하여 대화가 끊기지 않도록 함 ( 추후 논의 : FAILED로 변경 여부)
            savedFile.setStatus(FileStatus.COMPLETED);
            uploadedFileRepository.save(savedFile);
        }

        return UploadedFileResponse.from(savedFile);
    }

    /**
     * 메타데이터 생성 (UploadController용)
     */
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

    /**
     * 업로드 후처리 (FileController용) : Presigned URL로 올린 파일도 여기서 AI 인덱싱을 요청
     */
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