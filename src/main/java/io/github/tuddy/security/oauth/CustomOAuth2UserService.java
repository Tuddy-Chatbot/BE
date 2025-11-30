package io.github.tuddy.security.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
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
        OAuth2User oAuth2User = delegate.loadUser(req);

        String regId = req.getClientRegistration().getRegistrationId(); // naver | kakao
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String providerUserId;
        String email = null;
        String displayName = null;

        // 1. 소셜별 데이터 추출
        switch (regId) {
            case "naver" -> {
                Map<String, Object> resp = (Map<String, Object>) attrs.get("response");
                providerUserId = (String) resp.get("id");
                email = (String) resp.get("email");
                displayName = (String) resp.getOrDefault("name", email);
            }
            case "kakao" -> {
                providerUserId = String.valueOf(attrs.get("id"));
                Map<String, Object> kakaoAccount = (Map<String, Object>) attrs.get("kakao_account");
                if (kakaoAccount != null) {
                    // [수정] Kakao 이메일 추출 추가
                    email = (String) kakaoAccount.get("email");

                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                    if (profile != null) {
                        displayName = (String) profile.get("nickname");
                    }
                }
            }
            default -> throw new OAuth2AuthenticationException("unsupported_provider");
        }

        AuthProvider providerEnum = AuthProvider.valueOf(regId.toUpperCase());

        // 2. DB 저장 및 갱신 로직 (Lambda 캡처 문제 해결을 위해 if-else로 변경)
        Optional<UserAccount> existingOpt = users.findByProviderAndProviderUserId(providerEnum, providerUserId);
        UserAccount ua;

        if (existingOpt.isPresent()) {
            // 기존 회원 정보 업데이트
            ua = existingOpt.get();
            boolean updated = false;

            if (email != null && !email.equals(ua.getEmail())) {
                ua.setEmail(email);
                updated = true;
            }
            if (displayName != null && !displayName.isBlank() && !displayName.equals(ua.getDisplayName())) {
                ua.setDisplayName(displayName);
                updated = true;
            }

            if (updated) {
                ua = users.save(ua);
            }
        } else {
            // 신규 회원 생성
            String initialDisplayName = (displayName != null && !displayName.isBlank())
                    ? displayName
                    : "User-" + UUID.randomUUID().toString().substring(0, 8);

            ua = users.save(UserAccount.builder()
                    .provider(providerEnum)
                    .providerUserId(providerUserId)
                    .email(email) // Kakao의 경우 null일 수 있음 (허용)
                    .displayName(initialDisplayName)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());
        }

        // 3. 핸들러 전달용 Attributes 구성
        Map<String, Object> newAttributes = new HashMap<>(attrs);
        newAttributes.put("db_id", ua.getId());

        String userNameAttributeName = req.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                newAttributes,
                userNameAttributeName
        );
    }
}