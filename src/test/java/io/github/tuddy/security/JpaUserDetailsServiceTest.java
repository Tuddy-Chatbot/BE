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
        assertThat(userDetails.getPassword()).isEqualTo("encoded_password");
    }

    // [수정됨] 이제 이메일 로그인을 지원하지 않으므로, 이메일 입력 시 예외가 발생해야 정상입니다.
    @DisplayName("이메일로 조회 시 실패 (로그인 ID만 허용)")
    @Test
    void 이메일로_조회시_실패_예외_발생() {
        // Given
        String inputEmail = "test@test.com";

        // 사용자가 이메일을 아이디 입력창에 넣었을 때,
        // 로직은 findByLoginId(inputEmail)을 호출하게 되고, 결과는 없어야 함(Empty)
        given(users.findByLoginId(inputEmail)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(inputEmail))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("user_not_found");
    }

    @DisplayName("사용자 정보가 없으면 UsernameNotFoundException 발생")
    @Test
    void 사용자_없으면_예외_발생() {
        // Given
        given(users.findByLoginId("nonexistent")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("user_not_found");
    }
}