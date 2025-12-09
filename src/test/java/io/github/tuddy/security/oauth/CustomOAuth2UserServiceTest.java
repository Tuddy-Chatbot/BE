package io.github.tuddy.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @DisplayName("기존 소셜 로그인 사용자 재로그인 시 정보 업데이트 및 db_id 확인")
    @Test
    void 기존_사용자_업데이트_및_PK반환() {
        // Given
        Map<String, Object> newAttributes = Map.of("response",
                Map.of("id", "12345", "email", "new@naver.com", "name", "new_name"));
        OAuth2User oAuth2User = new DefaultOAuth2User(null, newAttributes, "response");

        when(mockDelegate.loadUser(mockUserRequest)).thenReturn(oAuth2User);

        UserAccount existingUser = UserAccount.builder()
                .id(100L) // 예상 DB ID
                .provider(AuthProvider.NAVER)
                .providerUserId("12345")
                .email("old@naver.com")
                .displayName("old_name")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        when(users.findByProviderAndProviderUserId(AuthProvider.NAVER, "12345"))
                .thenReturn(Optional.of(existingUser));
        when(users.save(any(UserAccount.class))).thenReturn(existingUser); // 업데이트 후 리턴 가정

        // When
        OAuth2User result = customOAuth2UserService.loadUser(mockUserRequest);

        // Then
        // 1. 정보 업데이트 검증
        assertThat(existingUser.getEmail()).isEqualTo("new@naver.com");

        // 2. [중요] db_id가 attributes에 포함되었는지 검증
        assertThat(result.getAttributes()).containsKey("db_id");
        assertThat(result.getAttributes().get("db_id")).isEqualTo(100L);
    }
}