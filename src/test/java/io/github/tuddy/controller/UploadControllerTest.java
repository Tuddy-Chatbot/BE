package io.github.tuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.repository.UploadedFileRepository;
import io.github.tuddy.security.WithMockAuthUser;
import io.github.tuddy.service.S3Service;

@WebMvcTest(UploadController.class)
@ActiveProfiles("test")
class UploadControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private S3Service s3Service;

    // UploadController가 UploadedFileRepository를 사용하므로 MockBean으로 추가
    @MockBean
    private UploadedFileRepository uploadedFileRepository;

    @DisplayName("Presigned PUT URL 발급 성공")
    @Test
    @WithMockAuthUser // id=1L인 사용자로 mock 인증
    void Presigned_PUT_URL_발급_성공() throws Exception {
        // Given
        // S3Service가 key를 생성하고 presigned URL을 반환하는 과정을 Mocking
        given(s3Service.buildKey(anyLong(), anyString())).willReturn("incoming/1/test-key");
        given(s3Service.presignPut(anyLong(), anyString(), anyString(), anyLong(), anyString()))
                .willReturn(Map.of("url", "http://s3-put-url.com"));
        // DB 저장이 성공적으로 이루어진다고 가정
        given(uploadedFileRepository.save(any(UploadedFile.class))).willAnswer(i -> i.getArgument(0));


        // When & Then
        mvc.perform(post("/s3/put")
                        .param("filename", "test.png")
                        .param("contentType", "image/png")
                        .param("contentLength", "1024")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://s3-put-url.com"));
    }

    @DisplayName("자신의 파일에 대한 Presigned GET URL 발급 성공")
    @Test
    @WithMockAuthUser(id = 123L) // id=123L인 사용자로 mock 인증
    void 자신의_파일_GET_URL_발급_성공() throws Exception {
        // Given
        String myFileKey = "incoming/123/2023/01/01/uuid-myfile.pdf";
        given(s3Service.presignGet(myFileKey)).willReturn("http://s3-get-url.com/myfile");

        // When & Then
        mvc.perform(get("/s3/get").param("key", myFileKey).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://s3-get-url.com/myfile"));
    }

    @DisplayName("타인의 파일에 대한 Presigned GET URL 요청 시 403 에러")
    @Test
    @WithMockAuthUser(id = 123L) // id=123L인 사용자로 mock 인증
    void 타인의_파일_GET_URL_요청시_403_에러() throws Exception {
        // Given
        String otherUserFileKey = "incoming/999/2023/01/01/uuid-otherfile.pdf"; // 다른 사용자(999)의 파일

        // When & Then
        mvc.perform(get("/s3/get").param("key", otherUserFileKey).with(csrf()))
                .andExpect(status().isForbidden()); // 403 Forbidden 예상
    }
}