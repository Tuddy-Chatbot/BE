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

  public Map<String, Object> presignPut(Long userId, String filename, String contentType, long contentLength) {
	    assertAllowed(contentType);
	    if (maxSize > 0 && contentLength > maxSize) {
	      throw new IllegalArgumentException("SIZE_LIMIT: max=" + maxSize + ", got=" + contentLength);
	    }

    String key = buildKey(userId, filename);

    var putReq = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .contentType(contentType)
        .serverSideEncryption("AES256") // SSE-S3
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
    resp.put("headers", signed.signedHeaders()); // 그대로 사용
    resp.put("expiresAt", signed.expiration().toString());
    return resp;
  }

  public String presignGet(String key) {
	    // 어떤 파일을 가져올지 정의하는 GetObjectRequest 객체를 생성
	    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
	            .bucket(bucket) // 버킷 이름 설정
	            .key(key)       // 파일의 전체 경로(key) 설정
	            .build();

	    // Presigned URL의 유효 시간 등 서명에 필요한 설정을 정의
	    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
	            .signatureDuration(ttl) // 기존 ttl 설정(15분)을 재사용
	            .getObjectRequest(getObjectRequest)
	            .build();

	    // S3Presigner를 사용해 최종적으로 서명된 요청(URL)을 생성
	    PresignedGetObjectRequest presignedGetObjectRequest = presigner.presignGetObject(presignRequest);

	    // 생성된 URL 객체에서 문자열 주소를 추출하여 반환
	    return presignedGetObjectRequest.url().toString();
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
