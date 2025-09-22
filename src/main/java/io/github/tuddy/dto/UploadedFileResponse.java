package io.github.tuddy.dto;

import java.time.LocalDateTime;

import io.github.tuddy.entity.file.UploadedFile;

// 파일 목록 API가 프론트엔드에 응답으로 보내줄 데이터의 형식을 정의
public record UploadedFileResponse(
    Long id,
    String originalFilename,
    LocalDateTime createdAt
) {
    public static UploadedFileResponse from(UploadedFile uploadedFile) {
        return new UploadedFileResponse(
            uploadedFile.getId(),
            uploadedFile.getOriginalFilename(),
            uploadedFile.getCreatedAt()
        );
    }
}