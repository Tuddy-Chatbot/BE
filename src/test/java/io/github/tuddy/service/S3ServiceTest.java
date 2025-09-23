package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @InjectMocks
    private S3Service s3Service;

    @Mock
    private S3Presigner presigner;

    @Mock
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "prefix", "incoming");
        ReflectionTestUtils.setField(s3Service, "maxSize", 1000L);
        ReflectionTestUtils.setField(s3Service, "allowedTypesCsv", "image/jpeg,image/png");
        ReflectionTestUtils.setField(s3Service, "ttl", Duration.ofMinutes(15));
        s3Service.init();
    }

    @DisplayName("presignPut 성공")
    @Test
    void presignPut_성공() throws Exception {
        // Given
        Long userId = 1L;
        String filename = "test.jpg";
        String contentType = "image/jpeg";
        long contentLength = 500L;
        String key = "incoming/1/test-key.jpg";

        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(new URL("http://mock.s3.url/test.jpg"));
        when(mockPresignedRequest.signedHeaders()).thenReturn(Map.of());
        when(mockPresignedRequest.expiration()).thenReturn(Instant.now().plus(Duration.ofMinutes(15)));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

        // When
        Map<String, Object> response = s3Service.presignPut(userId, filename, contentType, contentLength, key);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.get("url")).isEqualTo("http://mock.s3.url/test.jpg");
        assertThat(response.get("key")).isEqualTo(key);
        assertThat(response.get("expiresAt")).isNotNull();
    }

    @DisplayName("파일 크기 초과시 예외 발생")
    @Test
    void 파일크기_초과시_예외발생() {
        assertThatThrownBy(() ->
                s3Service.presignPut(1L, "big-file.jpg", "image/jpeg", 2000L, "some-key")
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SIZE_LIMIT");
    }

    @DisplayName("허용되지 않은 콘텐츠 타입시 예외 발생")
    @Test
    void 허용되지않은_콘텐츠타입시_예외발생() {
        assertThatThrownBy(() ->
                s3Service.presignPut(1L, "file.txt", "text/plain", 500L, "some-key")
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported content-type");
    }
}