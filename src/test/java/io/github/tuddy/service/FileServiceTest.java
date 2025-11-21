package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.tuddy.entity.file.FileStatus;
import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.repository.UploadedFileRepository;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FILE_ID = 10L;
    private static final String S3_KEY = "incoming/1/test-file.pdf";

    @InjectMocks
    private FileService fileService;

    @Mock private UploadedFileRepository uploadedFileRepository;
    @Mock private S3Service s3Service;
    @Mock private RagChatService ragChatService; // FastAPI 연동 서비스 Mock

    @DisplayName("1. 파일 메타데이터 생성 성공 (초기 상태 PENDING 확인)")
    @Test
    void 파일_메타데이터_생성_성공() {
        // Given
        given(uploadedFileRepository.save(any(UploadedFile.class))).willAnswer(i -> i.getArgument(0));

        // When
        UploadedFile result = fileService.createFileMetadata(USER_ID, "test.pdf", S3_KEY);

        // Then
        assertThat(result.getOriginalFilename()).isEqualTo("test.pdf");
        assertThat(result.getS3Key()).isEqualTo(S3_KEY);
        assertThat(result.getStatus()).isEqualTo(FileStatus.PENDING); // PENDING 상태 저장 확인
    }

    @DisplayName("2. 파일 처리 요청 성공 (상태 PENDING -> PROCESSING -> COMPLETED)")
    @Test
    void 파일_처리_요청_성공() {
        // Given
        var mockFile = UploadedFile.builder().id(FILE_ID).s3Key(S3_KEY).status(FileStatus.PENDING).build();
        given(uploadedFileRepository.findByIdAndUserAccountId(FILE_ID, USER_ID)).willReturn(Optional.of(mockFile));

        // When
        fileService.processUploadedFile(USER_ID, FILE_ID);

        // Then
        // 1. 상태가 PROCESSING으로 변경되었는지 확인 (saveAndFlush 호출)
        verify(uploadedFileRepository, times(1)).saveAndFlush(mockFile);
        assertThat(mockFile.getStatus()).isEqualTo(FileStatus.COMPLETED); // 최종 상태 COMPLETED 확인

        // 2. FastAPI OCR 요청이 호출되었는지 확인
        verify(ragChatService, times(1)).sendOcrRequest(eq(String.valueOf(USER_ID)), eq(S3_KEY));
    }

    @DisplayName("3. 파일 처리 요청 실패 시 (FastAPI 오류) -> 상태 FAILED 확인")
    @Test
    void 파일_처리_요청_실패_상태_FAILED_확인() {
        // Given
        var mockFile = UploadedFile.builder().id(FILE_ID).s3Key(S3_KEY).status(FileStatus.PENDING).build();
        given(uploadedFileRepository.findByIdAndUserAccountId(FILE_ID, USER_ID)).willReturn(Optional.of(mockFile));

        // FastAPI 호출 시 예외 발생을 Mocking
        doThrow(new RuntimeException("FastAPI Connection Error"))
            .when(ragChatService).sendOcrRequest(anyString(), anyString());

        // When & Then
        assertThatThrownBy(() -> fileService.processUploadedFile(USER_ID, FILE_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("File processing failed");

        // 1. 상태가 PROCESSING으로 변경되었다가 (saveAndFlush 호출은 성공)
        // 2. 최종적으로 FAILED로 변경되었는지 확인
        assertThat(mockFile.getStatus()).isEqualTo(FileStatus.FAILED);
    }
}