package io.github.tuddy.security.oauth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import io.github.tuddy.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private UserAccountRepository users;

    @Mock
    private DefaultOAuth2UserService mockDelegate;

    @BeforeEach
    void setUp() {
        // @InjectMocks 대신 수동으로 객체 생성 및 Mock 주입
        customOAuth2UserService = new CustomOAuth2UserService(users);
        ReflectionTestUtils.setField(customOAuth2UserService, "delegate", mockDelegate);
    }

    @DisplayName("신규 소셜 로그인 사용자 생성")
    @Test
    void 신규_소셜로그인_사용자_생성() {
        // Given
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("naver")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("id").redirectUri("uri").authorizationUri("uri").tokenUri("uri").userInfoUri("uri")
                .userNameAttributeName("response").build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", null, null);
        var userRequest = new OAuth2UserRequest(clientRegistration, accessToken);

        Map<String, Object> attributes = Map.of("response",
                Map.of("id", "12345", "email", "test@naver.com", "name", "네이버유저"));
        OAuth2User oAuth2User = new DefaultOAuth2User(null, attributes, "response");

        // Mock Delegate 설정: delegate.loadUser가 호출되면 준비된 oAuth2User를 반환하도록 설정
        when(mockDelegate.loadUser(userRequest)).thenReturn(oAuth2User);

        // DB에 없는 사용자라고 가정
        when(users.findByProviderAndProviderUserId(AuthProvider.NAVER, "12345"))
                .thenReturn(Optional.empty());

        // When
        // 올바른 시그니처로 메서드 호출
        customOAuth2UserService.loadUser(userRequest);

        // Then
        verify(users).save(any(UserAccount.class)); // save 메서드가 호출되었는지 검증
    }

    @DisplayName("기존 소셜 로그인 사용자")
    @Test
    void 기존_소셜로그인_사용자() {
        // Given
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("naver").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).clientId("id").redirectUri("uri").authorizationUri("uri").tokenUri("uri").userInfoUri("uri").userNameAttributeName("response").build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", null, null);
        var userRequest = new OAuth2UserRequest(clientRegistration, accessToken);
        Map<String, Object> attributes = Map.of("response", Map.of("id", "12345", "email", "test@naver.com", "name", "네이버유저"));
        OAuth2User oAuth2User = new DefaultOAuth2User(null, attributes, "response");

        when(mockDelegate.loadUser(userRequest)).thenReturn(oAuth2User);

        // 이미 DB에 존재하는 사용자라고 가정
        when(users.findByProviderAndProviderUserId(AuthProvider.NAVER, "12345"))
                .thenReturn(Optional.of(UserAccount.builder().build()));

        // When
        customOAuth2UserService.loadUser(userRequest);

        // Then
        verify(users, never()).save(any(UserAccount.class)); // 기존 사용자는 save를 호출하지 않음
    }
}