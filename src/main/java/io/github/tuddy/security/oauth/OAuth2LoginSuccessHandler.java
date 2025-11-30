package io.github.tuddy.security.oauth;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.UserAccountRepository;
import io.github.tuddy.security.AuthUser;
import io.github.tuddy.security.jwt.JwtTokenProvider;
import io.github.tuddy.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;
    private final UserAccountRepository users;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) auth.getPrincipal();

        // 1. [수정됨] CustomOAuth2UserService에서 넣어준 "db_id" 추출
        // 이 방식은 이메일 중복/누락 문제 없이 100% 정확하게 내 DB의 사용자를 찾을 수 있습니다.
        Long userId = (Long) oAuth2User.getAttribute("db_id");

        if (userId == null) {
            // 로직상 발생하기 힘들지만, 만약 db_id가 없다면 에러 처리
            log.error("OAuth2 Login Failed: db_id not found in attributes.");
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication Failed");
            return;
        }

        // 2. DB에서 사용자 정보 조회 (권한 정보 등 최신화)
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found (ID: " + userId + ")"));

        // 3. [중요] 토큰 발급을 위한 AuthUser 객체 생성
        // JwtTokenProvider는 AuthUser 타입의 Principal을 기대하므로 변환이 필수입니다.
        AuthUser authUser = new AuthUser(
                user.getId(),
                user.getLoginId() != null ? user.getLoginId() : user.getEmail(), // username
                "", // password (소셜 로그인은 비번 없음)
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );

        // 새로운 Authentication 객체 생성
        Authentication newAuth = new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());

        // 4. 토큰 발급 (Access / Refresh)
        String accessToken = jwtTokenProvider.createAccessToken(newAuth);
        String refreshToken = tokenService.issueRefreshToken(newAuth);

        // 5. 프론트엔드로 리다이렉트 (Query Param에 토큰 전달)
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        res.sendRedirect(targetUrl);
    }
}