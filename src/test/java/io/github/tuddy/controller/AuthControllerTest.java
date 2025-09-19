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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.LoginRequest;
import io.github.tuddy.dto.RegisterLocalRequest;

// exclude 대신 properties 속성을 사용하여 자동 설정을 제외
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @DisplayName("회원가입 및 로그인 성공 후 내 정보 조회")
    @Test
    void 회원가입_로그인_내정보조회_성공() throws Exception {
        // 1. 회원가입
        var registerReq = new RegisterLocalRequest("테스터", "testuser", "test@test.com", "password123", "password123");
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(registerReq))
                        .with(csrf()))
                .andExpect(status().isOk());

        // 2. 로그인
        var loginReq = new LoginRequest("testuser", "password123");
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(loginReq))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        // 3. 세션 획득 및 내 정보 조회
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("testuser"))
                .andExpect(jsonPath("$.displayName").value("테스터"));
    }

    @DisplayName("인증 없이 보호된 API 호출시 401 에러")
    @Test
    void 인증없이_보호된API_호출시_401에러() throws Exception {
        mvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @DisplayName("로그아웃 성공")
    @Test
    void 로그아웃_성공() throws Exception {
        // 1. 회원가입 및 로그인
        var registerReq = new RegisterLocalRequest("로그아웃테스터", "logoutuser", "logout@test.com", "password123", "password123");
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(registerReq))
                        .with(csrf()))
                .andExpect(status().isOk());

        var loginReq = new LoginRequest("logoutuser", "password123");
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(loginReq))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();

        // 2. 로그아웃 요청
        mvc.perform(post("/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        // 3. 로그아웃 후 보호된 API 접근 시 401 에러 확인
        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

}