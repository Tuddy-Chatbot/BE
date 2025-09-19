package io.github.tuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatProxyRequest;
import io.github.tuddy.dto.ChatProxyResponse;
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

    @DisplayName("RAG 채팅 성공")
    @Test
    @WithMockAuthUser
    void RAG_채팅_성공() throws Exception {
        // Given
        var requestDto = new ChatProxyRequest(1L, "안녕하세요");
        var responseDto = new ChatProxyResponse(1L, "RAG 챗봇 답변입니다.");
        given(chatService.processRagChat(eq(1L), any(ChatProxyRequest.class))).willReturn(responseDto);

        // When & Then
        mvc.perform(post("/chat/rag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(requestDto))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(om.writeValueAsString(responseDto)));
    }

    @DisplayName("일반 채팅 성공")
    @Test
    @WithMockAuthUser
    void 일반_채팅_성공() throws Exception {
        // Given
        var requestDto = new ChatProxyRequest(1L, "안녕");
        var responseDto = new ChatProxyResponse(1L, "일반 챗봇 답변입니다.");
        given(chatService.processNormalChat(eq(1L), any(ChatProxyRequest.class))).willReturn(responseDto);

        // When & Then
        mvc.perform(post("/chat/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(requestDto))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(om.writeValueAsString(responseDto)));
    }

    @DisplayName("채팅 요청시 query가 없으면 400 에러")
    @Test
    @WithMockAuthUser
    void 채팅_요청시_query가_없으면_400_에러() throws Exception {
        // Given
        String json = "{\"sessionId\": 1}"; // query 필드 누락

        // When & Then
        mvc.perform(post("/chat/rag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}