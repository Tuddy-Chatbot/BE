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

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    /**
     * 서버 직접 업로드 : 기존 규칙 준수 raw/{userId}/{uuid}_{filename}
     */
    public String upload(MultipartFile file, Long userId) {
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
     * Presigned URL Key 생성
     */
    public String buildKey(Long userId, String filename) {
        return "raw/" + userId + "/" + UUID.randomUUID() + "_" + filename;
    }

    public Map<String, Object> presignPut(Long userId, String filename, String contentType, long contentLength, String key) {
        long limit = 100 * 1024 * 1024;
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