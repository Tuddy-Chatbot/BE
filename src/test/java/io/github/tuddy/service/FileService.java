// 파일 위치: src/test/java/io/github/tuddy/service/FileServiceTest.java
package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import io.github.tuddy.dto.UploadedFileResponse;
import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.repository.UploadedFileRepository;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @InjectMocks
    private FileService fileService;

    @Mock
    private UploadedFileRepository uploadedFileRepository;
    @Mock
    private S3Service s3Service;

    @DisplayName("내 파일 목록 조회")
    @Test
    void 내_파일목록_조회() {
        // Given
        Long userId = 1L;
        var fileEntity = UploadedFile.builder().id(1L).originalFilename("test.pdf").build();
        given(uploadedFileRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userId)).willReturn(List.of(fileEntity));

        // When
        List<UploadedFileResponse> result = fileService.getMyFiles(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).originalFilename()).isEqualTo("test.pdf");
    }

    @DisplayName("파일 삭제 성공")
    @Test
    void 파일_삭제_성공() {
        // Given
        Long userId = 1L;
        Long fileId = 10L;
        var fileEntity = UploadedFile.builder().id(fileId).s3Key("s3/key/path").build();
        given(uploadedFileRepository.findByIdAndUserAccountId(fileId, userId)).willReturn(Optional.of(fileEntity));

        // When
        fileService.deleteFile(userId, fileId);

        // Then
        verify(s3Service, times(1)).deleteFile("s3/key/path"); // S3 삭제가 1번 호출되었는지 확인
        verify(uploadedFileRepository, times(1)).delete(fileEntity); // DB 삭제가 1번 호출되었는지 확인
    }

    @DisplayName("타인의 파일 삭제 시 예외 발생")
    @Test
    void 타인의_파일_삭제시_예외발생() {
        // Given: DB에서 파일을 찾지 못하는 상황을 가정 (본인 소유가 아니므로)
        Long userId = 1L;
        Long otherUserFileId = 20L;
        given(uploadedFileRepository.findByIdAndUserAccountId(otherUserFileId, userId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> fileService.deleteFile(userId, otherUserFileId))
                .isInstanceOf(AccessDeniedException.class);
    }
}