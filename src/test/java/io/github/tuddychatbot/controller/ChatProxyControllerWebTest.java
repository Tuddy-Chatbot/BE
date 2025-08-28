package io.github.tuddychatbot.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddychatbot.dto.ChatProxyRequest;
import io.github.tuddychatbot.service.RagChatService;

@WebMvcTest(ChatProxyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatProxyControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean RagChatService ragChatService;

    @Test
    void chat_ok_returns_service_body() throws Exception {
        String serviceBody = "{\"answer\":\"ok\"}";
        given(ragChatService.relay(any())).willReturn(serviceBody);

        String json = om.writeValueAsString(new ChatProxyRequest("alice", "hello"));

        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(content().json(serviceBody));
    }

    @Test
    void chat_validation_error_returns_400() throws Exception {
        // userId 누락 -> @Valid 실패
        String json = """
            { "query": "hello" }
            """;

        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json))
           .andExpect(status().isBadRequest());
    }
}
