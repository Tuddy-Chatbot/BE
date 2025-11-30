package io.github.tuddy.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.entity.user.UserRole;
import io.github.tuddy.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class JpaUserDetailsServiceTest {

    @InjectMocks
    private JpaUserDetailsService userDetailsService;

    @Mock
    private UserAccountRepository users;

    @DisplayName("로그인 ID로 사용자 정보 조회 성공")
    @Test
    void 로그인_ID로_사용자_조회_성공() {
        // Given
        var userAccount = UserAccount.builder()
                .id(1L)
                .loginId("testuser")
                .passwordHash("encoded_password")
                .role(UserRole.USER)
                .build();
        given(users.findByLoginId("testuser")).willReturn(Optional.of(userAccount));

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        // Then
        assertThat(userDetails.getUsername()).isEqualTo("testuser");
    }

    @DisplayName("이메일로 조회 시 실패 (로그인 ID만 허용)")
    @Test
    void 이메일로_조회시_실패_예외_발생() {
        // Given
        String inputEmail = "test@test.com";
        given(users.findByLoginId(inputEmail)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(inputEmail))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("user_not_found");
    }
}