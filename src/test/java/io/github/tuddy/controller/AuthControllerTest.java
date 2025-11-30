package io.github.tuddy.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.LoginRequest;
import io.github.tuddy.dto.RegisterLocalRequest;
import io.github.tuddy.dto.TokenResponse;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;

    @DisplayName("회원가입 -> 로그인 -> 토큰 획득 -> 내 정보 조회 (JWT)")
    @Test
    void 회원가입_로그인_내정보조회_성공() throws Exception {
        // 1. 회원가입
        var registerReq = new RegisterLocalRequest("테스터", "testuser", "test@test.com", "password123", "password123");
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(registerReq))
                        .with(csrf()))
                .andExpect(status().isOk());

        // 2. 로그인 및 토큰 추출
        var loginReq = new LoginRequest("testuser", "password123");
        String responseBody = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(loginReq))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        // 응답 JSON에서 Access Token 파싱
        TokenResponse tokens = om.readValue(responseBody, TokenResponse.class);

        // 3. JWT 헤더를 사용하여 내 정보 조회 (Session 아님)
        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("testuser"));
    }

    @DisplayName("인증 없이 보호된 API 호출시 401 에러")
    @Test
    void 인증없이_보호된API_호출시_401에러() throws Exception {
        mvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized());
    }
}