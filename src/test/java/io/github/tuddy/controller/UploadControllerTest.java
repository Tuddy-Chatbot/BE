package io.github.tuddy.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.github.tuddy.config.SecurityConfig;
import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.repository.UploadedFileRepository;
import io.github.tuddy.security.WithMockAuthUser;
import io.github.tuddy.security.jwt.JwtTokenProvider;
import io.github.tuddy.security.oauth.CustomOAuth2UserService;
import io.github.tuddy.security.oauth.OAuth2LoginSuccessHandler;
import io.github.tuddy.service.FileService;
import io.github.tuddy.service.S3Service;

@WebMvcTest(UploadController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UploadControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private S3Service s3Service;
    @MockBean private FileService fileService;
    @MockBean private UploadedFileRepository uploadedFileRepository;

    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean private UserDetailsService userDetailsService;

    @DisplayName("Presigned PUT URL 발급 성공")
    @Test
    @WithMockAuthUser
    void Presigned_PUT_URL_발급_성공() throws Exception {
        // [수정] Map.of() 대신 변경 가능한 HashMap 사용
        Map<String, Object> mutableMap = new HashMap<>();
        mutableMap.put("url", "http://s3-put-url.com");

        given(s3Service.buildKey(anyLong(), anyString())).willReturn("incoming/1/test-key");
        given(s3Service.presignPut(anyLong(), anyString(), anyString(), anyLong(), anyString()))
                .willReturn(mutableMap);

        UploadedFile savedFile = UploadedFile.builder().id(100L).build();
        given(fileService.createFileMetadata(anyLong(), anyString(), anyString())).willReturn(savedFile);

        mvc.perform(post("/s3/put")
                        .param("filename", "test.png")
                        .param("contentType", "image/png")
                        .param("contentLength", "1024")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://s3-put-url.com"))
                .andExpect(jsonPath("$.fileId").value(100L));
    }

    @DisplayName("자신의 파일에 대한 Presigned GET URL 발급 성공")
    @Test
    @WithMockAuthUser(id = 123L)
    void 자신의_파일_GET_URL_발급_성공() throws Exception {
        String myFileKey = "incoming/123/2023/01/01/uuid-myfile.pdf";
        given(s3Service.presignGet(myFileKey)).willReturn("http://s3-get-url.com/myfile");

        mvc.perform(get("/s3/get").param("key", myFileKey).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://s3-get-url.com/myfile"));
    }

    @DisplayName("타인의 파일에 대한 Presigned GET URL 요청 시 403 에러")
    @Test
    @WithMockAuthUser(id = 123L)
    void 타인의_파일_GET_URL_요청시_403_에러() throws Exception {
        String otherUserFileKey = "incoming/999/2023/01/01/uuid-otherfile.pdf";

        mvc.perform(get("/s3/get").param("key", otherUserFileKey).with(csrf()))
                .andExpect(status().isForbidden());
    }
}