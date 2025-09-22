// 파일 위치: src/test/java/io/github/tuddy/controller/FileControllerTest.java
package io.github.tuddy.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tuddy.dto.UploadedFileResponse;
import io.github.tuddy.security.WithMockAuthUser;
import io.github.tuddy.service.FileService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(jsonPath("$[0].originalFilename").value("report.pdf"));
    }

    @DisplayName("파일 삭제 API 성공")
    @Test
    @WithMockAuthUser
    void 파일_삭제_API_성공() throws Exception {
        // When & Then
        mvc.perform(delete("/files/1") // 1번 파일을 삭제 요청
                        .with(csrf()))
                .andExpect(status().isNoContent()); // 204 No Content 응답 확인
    }
}