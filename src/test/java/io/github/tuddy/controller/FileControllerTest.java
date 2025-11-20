package io.github.tuddy.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.github.tuddy.dto.UploadedFileResponse;
import io.github.tuddy.security.WithMockAuthUser;
import io.github.tuddy.service.FileService;

@WebMvcTest(FileController.class)
@ActiveProfiles("test")
class FileControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FileService fileService;

    @DisplayName("내 파일 목록 조회 API 성공")
    @Test
    @WithMockAuthUser(id = 1L)
    void 내_파일목록_조회_API_성공() throws Exception {
        // Given
        var fileResponse = new UploadedFileResponse(1L, "report.pdf", LocalDateTime.now());
        given(fileService.getMyFiles(1L)).willReturn(List.of(fileResponse));

        // When & Then
        mvc.perform(get("/files")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].originalFilename").value("report.pdf"));
    }

    @DisplayName("파일 삭제 API 성공")
    @Test
    @WithMockAuthUser(id = 1L) // 1L 사용자로 인증
    void 파일_삭제_API_성공() throws Exception {
        // Given
        // FileService의 deleteFile 메서드가 아무것도 반환하지 않으므로(void),
        // given() 설정 없이도 호출 자체를 검증
        // 또는 doNothing().when(fileService).deleteFile(...) 로 명시적으로 설정 가능

        // When & Then
        mvc.perform(delete("/files/10") // 1L 사용자가 10번 파일을 삭제 요청
                        .with(csrf()))
                .andExpect(status().isNoContent()); // 204 No Content 응답 확인
    }
}