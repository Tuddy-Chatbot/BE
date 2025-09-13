/* package io.github.tuddychatbot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import io.github.tuddy.dto.RegisterLocalRequest;
import io.github.tuddy.service.AuthService;
import io.github.tuddy.service.RagChatService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:tuddy2;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.flyway.enabled=true",
  "logging.level.org.springframework.security=INFO"
})
class AuthFlowIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired AuthService authService;
  @Autowired CapturingRagChatService rag;

  @Test
  void login_then_call_rag_chat_uses_pk_as_namespace() throws Exception {
    var ua = authService.registerLocal(new RegisterLocalRequest("홍길동","gildong","x@y.z","Passw0rd!","Passw0rd!"));

    var login = post("/api/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"loginIdOrEmail\":\"gildong\",\"password\":\"Passw0rd!\"}");
    var res = mvc.perform(login).andExpect(status().isOk()).andReturn();
    var session = res.getRequest().getSession(false);

    mvc.perform(post("/rag/chat")
        .session((org.springframework.mock.web.MockHttpSession) session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"query\":\"hello\"}"))
      .andExpect(status().isOk())
      .andExpect(content().string("{\"ok\":true}"));

    org.assertj.core.api.Assertions.assertThat(rag.lastUserId).isEqualTo(ua.getId());
    org.assertj.core.api.Assertions.assertThat(rag.lastQuery).isEqualTo("hello");
  }

  @TestConfiguration
  static class Stub {
    @Bean @Primary
    CapturingRagChatService ragChatService() { return new CapturingRagChatService(); }
  }

  static class CapturingRagChatService extends RagChatService {
    volatile Long lastUserId; volatile String lastQuery;
    CapturingRagChatService() { super(RestClient.create(), "/x", "/y"); }
    @Override public String relay(Long userId, String query) { this.lastUserId=userId; this.lastQuery=query; return "{\"ok\":true}"; }
    @Override public String relayNormal(Long userId, String query){ this.lastUserId=userId; this.lastQuery=query; return "{\"ok\":true}"; }
  }
}
*/
