package io.github.tuddy.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.tuddy.entity.user.AuthProvider;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.entity.user.UserRole;
import io.github.tuddy.entity.user.UserStatus;
import io.github.tuddy.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    private CustomOAuth2UserService customOAuth2UserService;

    @Mock private UserAccountRepository users;
    @Mock private DefaultOAuth2UserService mockDelegate;

    private ClientRegistration naverClient;
    private OAuth2UserRequest mockUserRequest;

    @BeforeEach
    void setUp() {
        customOAuth2UserService = new CustomOAuth2UserService(users);
        ReflectionTestUtils.setField(customOAuth2UserService, "delegate", mockDelegate);

        naverClient = ClientRegistration.withRegistrationId("naver")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("id").redirectUri("uri").authorizationUri("uri").tokenUri("uri").userInfoUri("uri")
                .userNameAttributeName("response").build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", null, null);
        mockUserRequest = new OAuth2UserRequest(naverClient, accessToken);
    }

    // ... (기존 신규 사용자 생성 테스트는 유지)

    @DisplayName("2. 기존 소셜 로그인 사용자 재로그인 시 정보 업데이트")
    @Test
    void 기존_소셜로그인_사용자_정보_업데이트() {
        // Given
        // 1. 소셜 API에서 새로운 정보 수신
        Map<String, Object> newAttributes = Map.of("response",
                Map.of("id", "12345", "email", "new_test@naver.com", "name", "새로운네이버닉네임"));
        OAuth2User oAuth2User = new DefaultOAuth2User(null, newAttributes, "response");

        when(mockDelegate.loadUser(mockUserRequest)).thenReturn(oAuth2User);

        // 2. DB에 존재하는 기존 사용자 정보 (오래된 정보)
        UserAccount existingUser = UserAccount.builder()
                .id(1L)
                .provider(AuthProvider.NAVER)
                .providerUserId("12345")
                .email("old_test@naver.com") // 이전 이메일
                .displayName("오래된닉네임") // 이전 닉네임
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        when(users.findByProviderAndProviderUserId(AuthProvider.NAVER, "12345"))
                .thenReturn(Optional.of(existingUser));

        // When
        customOAuth2UserService.loadUser(mockUserRequest);

        // Then
        // 1. 정보가 업데이트 되었는지 확인
        assertThat(existingUser.getEmail()).isEqualTo("new_test@naver.com");
        assertThat(existingUser.getDisplayName()).isEqualTo("새로운네이버닉네임");

        // 2. save가 호출되어 DB에 변경 사항이 반영되었는지 확인
        verify(users, times(1)).save(existingUser);
    }

    @DisplayName("3. 기존 소셜 로그인 사용자 재로그인 시 정보가 동일하면 업데이트 Skip")
    @Test
    void 기존_소셜로그인_사용자_정보_동일하면_업데이트_Skip() {
        // Given
        // 1. 소셜 API에서 동일한 정보 수신
        Map<String, Object> sameAttributes = Map.of("response",
                Map.of("id", "67890", "email", "same@naver.com", "name", "동일닉네임"));
        OAuth2User oAuth2User = new DefaultOAuth2User(null, sameAttributes, "response");

        when(mockDelegate.loadUser(mockUserRequest)).thenReturn(oAuth2User);

        // 2. DB에 존재하는 기존 사용자 정보 (동일한 정보)
        UserAccount existingUser = UserAccount.builder()
                .id(2L)
                .provider(AuthProvider.NAVER)
                .providerUserId("67890")
                .email("same@naver.com")
                .displayName("동일닉네임")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        when(users.findByProviderAndProviderUserId(AuthProvider.NAVER, "67890"))
                .thenReturn(Optional.of(existingUser));

        // When
        customOAuth2UserService.loadUser(mockUserRequest);

        // Then
        // save가 호출되지 않았는지 확인 (성능 최적화)
        verify(users, never()).save(any(UserAccount.class));
    }
}