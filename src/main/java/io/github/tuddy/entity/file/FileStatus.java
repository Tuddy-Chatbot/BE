package io.github.tuddy.entity.file;

public enum FileStatus {
    PENDING,    // S3 업로드 대기 또는 진행 중
    PROCESSING, // FastAPI에서 OCR/임베딩 처리 중
    COMPLETED,  // 처리 완료 (채팅 가능)
    FAILED      // 처리 실패
}