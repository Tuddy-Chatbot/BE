package io.github.tuddychatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import io.github.tuddychatbot.dto.RegisterLocalRequest;
import io.github.tuddychatbot.entity.user.AuthProvider;
import io.github.tuddychatbot.entity.user.UserAccount;
import io.github.tuddychatbot.repository.UserAccountRepository;

@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:tuddy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.flyway.enabled=true"
})
class AuthServiceTest {

  @Autowired AuthService authService;
  @Autowired UserAccountRepository users;

  @Test @Transactional
  void registerLocal_saves_user_with_LOCAL_provider() {
    var req = new RegisterLocalRequest("홍길동", "gildong", "a@b.c", "Passw0rd!", "Passw0rd!");
    UserAccount ua = authService.registerLocal(req);

    assertThat(ua.getId()).isNotNull();
    assertThat(ua.getProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(ua.getPasswordHash()).isNotBlank().isNotEqualTo("Passw0rd!");
    assertThat(users.findByLoginId("gildong")).isPresent();
  }

  @Test @Transactional
  void registerLocal_duplicated_loginId_throws() {
    authService.registerLocal(new RegisterLocalRequest("a","dup","a@a.com","Passw0rd!","Passw0rd!"));
    assertThatThrownBy(() ->
      authService.registerLocal(new RegisterLocalRequest("b","dup","b@b.com","Passw0rd!","Passw0rd!"))
    ).isInstanceOf(IllegalStateException.class).hasMessageContaining("login_id_in_use");
  }
}
