package io.github.tuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.config.SecurityConfig;
import io.github.tuddy.dto.ChatMessageResponse;
import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.ChatSessionResponse;
import io.github.tuddy.entity.chat.SenderType;
import io.github.tuddy.security.WithMockAuthUser;
import io.github.tuddy.security.jwt.JwtTokenProvider;
import io.github.tuddy.security.oauth.CustomOAuth2UserService;
import io.github.tuddy.security.oauth.OAuth2LoginSuccessHandler;
import io.github.tuddy.service.ChatService;

@WebMvcTest(ChatProxyController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ChatProxyControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @MockBean private ChatService chatService;

    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean private UserDetailsService userDetailsService;

    private MockMultipartFile createJsonPart(String name, Object dto) throws Exception {
        String json = om.writeValueAsString(dto);
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private String createJson(Object dto) throws Exception {
        return om.writeValueAsString(dto);
    }

    @DisplayName("1. 통합 채팅 엔드포인트 성공 (Normal Chat)")
    @Test
    @WithMockAuthUser(id = 1L)
    void 통합_채팅_엔드포인트_성공() throws Exception {
        // Given
        var requestDto = new ChatProxyRequest(1L, "안녕하세요", 0L);
        var responseDto = new ChatProxyResponse(1L, "통합 챗봇 답변입니다.");

        MockMultipartFile reqPart = createJsonPart("req", requestDto);

        // [수정] 파일 파트가 없는 경우 null이 넘어올 수 있으므로 anyList() 대신 any() 사용
        given(chatService.processChat(eq(1L), any(ChatProxyRequest.class), any()))
            .willReturn(responseDto);

        // When & Then
        mvc.perform(multipart("/chat")
                        .file(reqPart)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(createJson(responseDto)));
    }

    @DisplayName("2. 통합 채팅 엔드포인트 성공 (Image Chat)")
    @Test
    @WithMockAuthUser(id = 1L)
    void 통합_채팅_엔드포인트_이미지_성공() throws Exception {
        var requestDto = new ChatProxyRequest(1L, "이 이미지 뭐야?", 0L);
        var responseDto = new ChatProxyResponse(1L, "이미지 분석 답변입니다.");

        MockMultipartFile reqPart = createJsonPart("req", requestDto);
        MockMultipartFile imageFile = new MockMultipartFile("files", "test-image.jpg", "image/jpeg", "image_content".getBytes());

        given(chatService.processChat(eq(1L), any(ChatProxyRequest.class), any()))
            .willReturn(responseDto);

        mvc.perform(multipart("/chat")
                        .file(reqPart)
                        .file(imageFile)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(createJson(responseDto)));
    }

    @DisplayName("3. 내 채팅 세션 목록 조회 성공")
    @Test
    @WithMockAuthUser
    void 내_채팅세션_목록_조회_성공() throws Exception {
        var sessionResponse = new ChatSessionResponse(1L, "테스트 질문", LocalDateTime.now());
        given(chatService.getMyChatSessions(1L)).willReturn(List.of(sessionResponse));

        mvc.perform(get("/chat/sessions").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("테스트 질문"));
    }

    @DisplayName("4. 특정 채팅 메시지 목록 조회 성공 (Pagination)")
    @Test
    @WithMockAuthUser(id = 1L)
    void 특정_채팅_메시지_조회_성공_페이징() throws Exception {
        var messageResponse = new ChatMessageResponse(10L, SenderType.USER, "안녕하세요", LocalDateTime.now());
        var slice = new SliceImpl<>(List.of(messageResponse));

        given(chatService.getMessagesBySession(eq(1L), eq(1L), any()))
            .willReturn(slice);

        mvc.perform(get("/chat/sessions/1/messages")
                        .param("page", "0")
                        .param("size", "5")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10L));
    }

    @DisplayName("5. 타인의 채팅 메시지 조회 시 403 에러")
    @Test
    @WithMockAuthUser(id = 1L)
    void 타인의_채팅_메시지_조회시_403_에러() throws Exception {
        given(chatService.getMessagesBySession(eq(1L), eq(2L), any()))
            .willThrow(new AccessDeniedException("No permission"));

        mvc.perform(get("/chat/sessions/2/messages").with(csrf()))
                .andExpect(status().isForbidden());
    }
}