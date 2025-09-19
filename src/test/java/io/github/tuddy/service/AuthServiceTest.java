package io.github.tuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import io.github.tuddy.dto.RegisterLocalRequest;
import io.github.tuddy.entity.user.AuthProvider;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.UserAccountRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAccountRepository users;

    @DisplayName("회원가입 성공")
    @Test
    void 회원가입_성공() {
        // Given
        var req = new RegisterLocalRequest("홍길동", "gildong", "a@b.c", "Passw0rd!", "Passw0rd!");

        // When
        UserAccount ua = authService.registerLocal(req);

        // Then
        assertThat(ua.getId()).isNotNull();
        assertThat(ua.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(ua.getPasswordHash()).isNotBlank().isNotEqualTo("Passw0rd!");
        assertThat(users.findByLoginId("gildong")).isPresent();
    }

    @DisplayName("중복된 아이디로 가입시 예외 발생")
    @Test
    void 중복된_아이디로_가입시_예외발생() {
        // Given
        authService.registerLocal(new RegisterLocalRequest("사용자1", "duplicate_id", "a@a.com", "Passw0rd!", "Passw0rd!"));

        // When & Then
        assertThatThrownBy(() ->
                authService.registerLocal(new RegisterLocalRequest("사용자2", "duplicate_id", "b@b.com", "Passw0rd!", "Passw0rd!"))
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("login_id_in_use");
    }
}