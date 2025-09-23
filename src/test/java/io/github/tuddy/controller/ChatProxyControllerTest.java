package io.github.tuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart; // multipart import
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile; // MockMultipartFile import
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatMessageResponse;
import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
import io.github.tuddy.dto.ChatSessionResponse;
import io.github.tuddy.entity.chat.SenderType;
import io.github.tuddy.security.WithMockAuthUser;
import io.github.tuddy.service.ChatService;

@WebMvcTest(ChatProxyController.class)
@ActiveProfiles("test")
class ChatProxyControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ChatService chatService;

    @DisplayName("일반 텍스트 채팅 성공")
    @Test
    @WithMockAuthUser
    void 일반_채팅_성공() throws Exception {
        // Given
        var requestDto = new ChatProxyRequest(1L, "안녕", null);
        var responseDto = new ChatProxyResponse(1L, "일반 챗봇 답변입니다.");

        // DTO를 JSON 문자열로 변환
        String requestDtoJson = om.writeValueAsString(requestDto);
        // JSON 파트를 위한 MockMultipartFile 생성 (Content-Type 명시가 핵심!)
        MockMultipartFile reqPart = new MockMultipartFile(
            "req", "", MediaType.APPLICATION_JSON_VALUE, requestDtoJson.getBytes(StandardCharsets.UTF_8)
        );

        given(chatService.processChat(eq(1L), any(ChatProxyRequest.class), any())).willReturn(responseDto);

        // When & Then
        mvc.perform(multipart("/chat") // .post() 대신 .multipart()
                        .file(reqPart)      // .content() 대신 .file()로 파트 추가
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(om.writeValueAsString(responseDto)));
    }

    @DisplayName("이미지 첨부 채팅 성공")
    @Test
    @WithMockAuthUser
    void 이미지_첨부_채팅_성공() throws Exception {
        // Given
        var requestDto = new ChatProxyRequest(null, "이 이미지 뭐야?", null);
        var responseDto = new ChatProxyResponse(10L, "이미지 분석 결과입니다.");

        String requestDtoJson = om.writeValueAsString(requestDto);
        MockMultipartFile reqPart = new MockMultipartFile("req", "", "application/json", requestDtoJson.getBytes(StandardCharsets.UTF_8));

        // 테스트용 이미지 파일 생성
        MockMultipartFile imageFile = new MockMultipartFile("files", "test-image.jpg", "image/jpeg", "image_content".getBytes());

        given(chatService.processChat(eq(1L), any(ChatProxyRequest.class), any())).willReturn(responseDto);

        // When & Then
        mvc.perform(multipart("/chat")
                        .file(reqPart)
                        .file(imageFile) // 이미지 파일 파트 추가
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(om.writeValueAsString(responseDto)));
    }


    // =================================================================
    // 아래의 기존 테스트들은 변경할 필요 없이 그대로 두시면 됩니다.
    // =================================================================

    @DisplayName("내 채팅 세션 목록 조회 성공")
    @Test
    @WithMockAuthUser // id=1L인 사용자로 mock 인증
    void 내_채팅세션_목록_조회_성공() throws Exception {
        // Given: ChatService가 특정 목록을 반환하도록 설정
        var sessionResponse = new ChatSessionResponse(1L, "테스트 질문", LocalDateTime.now());
        given(chatService.getMyChatSessions(1L)).willReturn(List.of(sessionResponse));

        // When & Then
        mvc.perform(get("/chat/sessions")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].title").value("테스트 질문"));
    }

    @DisplayName("특정 채팅 메시지 목록 조회 성공")
    @Test
    @WithMockAuthUser // id=1L인 사용자로 mock 인증
    void 특정_채팅_메시지_조회_성공() throws Exception {
        // Given: ChatService가 특정 메시지 목록을 반환하도록 설정
        var messageResponse = new ChatMessageResponse(10L, SenderType.USER, "안녕하세요", LocalDateTime.now());
        given(chatService.getMessagesBySession(1L, 1L)).willReturn(List.of(messageResponse));

        // When & Then
        mvc.perform(get("/chat/sessions/1/messages")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].content").value("안녕하세요"));
    }

    @DisplayName("타인의 채팅 메시지 조회 시 403 에러")
    @Test
    @WithMockAuthUser(id = 1L) // 나는 1번 유저
    void 타인의_채팅_메시지_조회시_403_에러() throws Exception {
        // Given: ChatService가 AccessDeniedException을 발생시키도록 설정
        given(chatService.getMessagesBySession(1L, 2L)).willThrow(new AccessDeniedException("No permission"));

        // When & Then
        mvc.perform(get("/chat/sessions/2/messages")
                        .with(csrf()))
                .andExpect(status().isForbidden()); // 403 Forbidden 응답 확인
    }
}