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

    // 이메일 조회 시나리오 테스트
    @DisplayName("이메일로 사용자 정보 조회 성공")
    @Test
    void 이메일로_사용자_조회_성공() {
        // Given
        var userAccount = UserAccount.builder()
                .id(1L)
                .email("test@test.com")
                .passwordHash("encoded_password")
                .role(UserRole.USER)
                .build();
        // ID 조회는 실패하고, 이메일 조회는 성공하도록 설정
        given(users.findByLoginId("test@test.com")).willReturn(Optional.empty());
        given(users.findByEmail("test@test.com")).willReturn(Optional.of(userAccount));

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername("test@test.com");

        // Then
        // 코드 로직에 따라 email로 찾았어도 UserDetails의 username은 email이 됨
        assertThat(userDetails.getUsername()).isEqualTo("test@test.com");
        assertThat(userDetails.getPassword()).isEqualTo("encoded_password");
    }

    @DisplayName("사용자 정보가 없으면 UsernameNotFoundException 발생")
    @Test
    void 사용자_없으면_예외_발생() {
        // Given
        given(users.findByLoginId("nonexistent")).willReturn(Optional.empty());
        given(users.findByEmail("nonexistent")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("user_not_found");
    }
}