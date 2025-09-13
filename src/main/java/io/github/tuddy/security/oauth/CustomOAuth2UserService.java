package io.github.tuddy.security.oauth;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tuddy.entity.user.AuthProvider;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.entity.user.UserRole;
import io.github.tuddy.entity.user.UserStatus;
import io.github.tuddy.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
  private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
  private final UserAccountRepository users;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
    OAuth2User user = delegate.loadUser(req);
    String regId = req.getClientRegistration().getRegistrationId(); // google | naver | kakao
    Map<String, Object> attrs = user.getAttributes();

    String providerUserId;
    String email = null;
    String displayName = null;

    switch (regId) {
      case "naver" -> {
        Map<String,Object> resp = (Map<String,Object>) attrs.get("response");
        providerUserId = (String) resp.get("id");
        email = (String) resp.get("email");
        displayName = (String) resp.getOrDefault("name", email);
      }
      case "kakao" -> {
        providerUserId = String.valueOf(attrs.get("id"));
        Map<String,Object> kakaoAccount = (Map<String,Object>) attrs.get("kakao_account");
        if (kakaoAccount != null) {
          Map<String,Object> profile = (Map<String,Object>) kakaoAccount.get("profile");
          if (profile != null) {
            displayName = (String) profile.get("nickname");
          }
        }
      }
      default -> throw new OAuth2AuthenticationException("unsupported_provider");
    }

    AuthProvider providerEnum = AuthProvider.valueOf(regId.toUpperCase());

    var existing = users.findByProviderAndProviderUserId(providerEnum, providerUserId);

    if (existing.isEmpty()) {
      // 닉네임이 null일 경우를 대비한 방어 로직
      if (displayName == null || displayName.isBlank()) {
          displayName = "User-" + UUID.randomUUID().toString().substring(0, 8);
      }

      UserAccount ua = UserAccount.builder()
          .provider(providerEnum)
          .providerUserId(providerUserId)
          .email(email)
          .displayName(displayName)
          .role(UserRole.USER)
          .status(UserStatus.ACTIVE)
          .build();
      users.save(ua);
    }
    return user;
  }
}

