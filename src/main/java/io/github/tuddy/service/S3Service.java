package io.github.tuddy.service;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;       // 직접 업로드용
    private final S3Presigner s3Presigner; // URL 생성용

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    // [New] 서버 직접 업로드 (ChatService용)
    public String upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String s3Key = UUID.randomUUID().toString() + extension;

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putOb, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Direct upload success: {}", s3Key);
            return s3Key;

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    // [Legacy] Presigned URL Key 생성 (UploadController용) - 복구됨
    public String buildKey(Long userId, String filename) {
        return "raw/" + userId + "/" + UUID.randomUUID() + "_" + filename;
    }

    // [Legacy] PUT URL 생성 (UploadController용) - 복구됨
    public Map<String, Object> presignPut(Long userId, String filename, String contentType, long contentLength, String key) {
        long limit = 100 * 1024 * 1024; // 100MB
        if (contentLength > limit) {
             throw new IllegalArgumentException("SIZE_LIMIT_EXCEEDED: Max 100MB");
        }

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        String url = s3Presigner.presignPutObject(presignRequest).url().toString();

        Map<String, Object> res = new HashMap<>();
        res.put("url", url);
        res.put("key", key);
        return res;
    }

    // [Legacy] GET URL 생성 (UploadController용) - 복구됨
    public String presignGet(String key) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // 파일 삭제
    public void deleteFile(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
			return;
		}
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(s3Key).build());
        } catch (Exception e) {
            log.error("Failed to delete file: {}", s3Key, e);
        }
    }
}