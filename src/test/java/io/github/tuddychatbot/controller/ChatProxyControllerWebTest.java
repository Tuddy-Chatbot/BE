package io.github.tuddychatbot.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.controller.ChatProxyController;
import io.github.tuddy.security.AuthUser;
import io.github.tuddy.service.RagChatService;

@WebMvcTest(ChatProxyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatProxyControllerWebTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @MockBean RagChatService ragChatService;

  @BeforeEach
  void setAuth() {
    var principal = new AuthUser(123L, "gildong", "{noop}", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void chat_ok_returns_service_body() throws Exception {
    String serviceBody = "{\"answer\":\"ok\"}";
    given(ragChatService.relay(eq(123L), eq("hello"))).willReturn(serviceBody);

    String json = """
      { "query": "hello" }
    """;

    mvc.perform(post("/rag/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(json))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(content().json(serviceBody));
  }

  @Test
  void chat_validation_error_returns_400_when_query_missing() throws Exception {
    String json = "{}"; // query 누락

    mvc.perform(post("/rag/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(json))
      .andExpect(status().isBadRequest());
  }

  @Test
  void normalChat_ok_returns_service_body() throws Exception {
    given(ragChatService.relayNormal(eq(123L), eq("ping"))).willReturn("{\"reply\":\"pong\"}");

    String json = """
      { "query": "ping" }
    """;

    mvc.perform(post("/normal-chat")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(json))
      .andExpect(status().isOk())
      .andExpect(content().json("{\"reply\":\"pong\"}"));
  }

  @Test
  void normalChat_validation_error_returns_400_when_query_blank() throws Exception {
    String json = """
      { "query": "" }
    """;

    mvc.perform(post("/normal-chat")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(json))
      .andExpect(status().isBadRequest());
  }
}
