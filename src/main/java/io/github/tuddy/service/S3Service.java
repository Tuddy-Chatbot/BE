package io.github.tuddy.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${app.aws.s3.bucket}") private String bucket;
    @Value("${app.s3.prefix:incoming}") private String prefix;
    @Value("${app.s3.presign.ttl:PT15M}") private Duration ttl;
    @Value("${app.s3.allowed-types:}") private String allowedTypesCsv;
    @Value("${app.s3.max-size-bytes:0}") private long maxSize;

    private Set<String> allowedTypes;

    @PostConstruct
    void init() {
        allowedTypes = Arrays.stream(allowedTypesCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String buildKey(Long userId, String filename) {
        String safe = sanitize(filename);
        LocalDate d = LocalDate.now();
        return "%s/%d/%04d/%02d/%02d/%s-%s".formatted(
                prefix, userId == null ? 0 : userId,
                d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                UUID.randomUUID(), safe);
    }

    // ★★★ 1. 메서드 시그니처에 String key를 추가합니다. ★★★
    public Map<String, Object> presignPut(Long userId, String filename, String contentType, long contentLength, String key) {
        assertAllowed(contentType);
        if (maxSize > 0 && contentLength > maxSize) {
            throw new IllegalArgumentException("SIZE_LIMIT: max=" + maxSize + ", got=" + contentLength);
        }

        var putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key) // 전달받은 key를 사용합니다.
                .contentType(contentType)
                .serverSideEncryption("AES256")
                .build();

        var presignReq = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putReq)
                .build();

        PresignedPutObjectRequest signed = presigner.presignPutObject(presignReq);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("bucket", bucket);
        resp.put("key", key);
        resp.put("url", signed.url().toString());
        resp.put("headers", signed.signedHeaders());
        resp.put("expiresAt", signed.expiration().toString());
        return resp;
    }

    public String presignGet(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedGetObjectRequest = presigner.presignGetObject(presignRequest);

        return presignedGetObjectRequest.url().toString();
    }

    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.deleteObject(deleteObjectRequest);
    }

    private void assertAllowed(String contentType) {
        if (!allowedTypes.isEmpty() && !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content-type: " + contentType);
        }
    }

    private static String sanitize(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
