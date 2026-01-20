package io.github.tuddy.service;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final List<String> allowedTypes; // [추가] 허용 타입 목록

    public S3Service(S3Client s3Client,
                     S3Presigner s3Presigner,
                     @Value("${app.aws.s3.bucket}") String bucket,
                     @Value("#{'${app.s3.allowed-types}'.split(',')}") List<String> allowedTypes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.allowedTypes = allowedTypes;
    }

    /**
     * 파일 타입 검증 헬퍼 메서드
     */
    private void validateFileType(String contentType) {
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + contentType);
        }
    }

    /**
     * 서버 직접 업로드 : 기존 규칙 준수 raw/{userId}/{uuid}_{filename}
     */
    public String upload(MultipartFile file, Long userId) {
        // [추가] 파일 타입 검증
        validateFileType(file.getContentType());

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "unknown";
        }

        // 기존 구조와 동일하게 'raw/{userId}/' 경로 사용
        String s3Key = "raw/" + userId + "/" + UUID.randomUUID() + "_" + originalFilename;

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putOb, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("S3 Direct Upload Success: {}", s3Key);
            return s3Key;

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Presigned URL Key 생성 (기존 유지)
     */
    public String buildKey(Long userId, String filename) {
        return "raw/" + userId + "/" + UUID.randomUUID() + "_" + filename;
    }

    // [수정] 파일 타입 검증 로직 추가
    public Map<String, Object> presignPut(Long userId, String filename, String contentType, long contentLength, String key) {
        // 1. [추가] 파일 타입 검증
        validateFileType(contentType);

        // 2. 파일 크기 검증 (기존 유지)
        long limit = 100 * 1024 * 1024; // 100MB
        if (contentLength > limit) {
            throw new IllegalArgumentException("SIZE_LIMIT_EXCEEDED");
        }

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(objectRequest)
                .build();

        String url = s3Presigner.presignPutObject(presignRequest).url().toString();

        Map<String, Object> res = new HashMap<>();
        res.put("url", url);
        res.put("key", key);
        return res;
    }

    public String presignGet(String key) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public void deleteFile(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(s3Key).build());
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", s3Key, e);
        }
    }
}