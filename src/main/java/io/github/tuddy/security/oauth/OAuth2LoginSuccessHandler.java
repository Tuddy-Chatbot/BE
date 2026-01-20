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

import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.UserAccountRepository;
import io.github.tuddy.security.AuthUser;
import io.github.tuddy.security.jwt.JwtTokenProvider;
import io.github.tuddy.service.TokenService;
import jakarta.servlet.http.Cookie; // [추가]
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

        // 1. CustomOAuth2UserService에서 넣어준 "db_id" 추출 (기존 유지)
        Long userId = (Long) oAuth2User.getAttribute("db_id");

        if (userId == null) {
            log.error("OAuth2 Login Failed: db_id not found in attributes.");
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication Failed");
            return;
        }

        // 2. DB에서 사용자 정보 조회 (기존 유지)
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found (ID: " + userId + ")"));

        // 3. AuthUser 객체 생성 (기존 유지)
        AuthUser authUser = new AuthUser(
                user.getId(),
                user.getLoginId() != null ? user.getLoginId() : user.getEmail(),
                "",
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );

        Authentication newAuth = new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());

        // 4. 토큰 발급
        // AccessToken은 여기서 생성했지만 URL에 싣지 않고 버립니다 (프론트가 refresh 요청으로 새로 받을 것임)
        String refreshToken = tokenService.issueRefreshToken(newAuth);

        // 5. Refresh Token을 HttpOnly 쿠키에 저장
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true); // 자바스크립트 접근 불가 (XSS 방지)
        refreshCookie.setSecure(true);  // 로컬(http)은 false, 배포(https)는 true로 변경 필요
        refreshCookie.setPath("/");      // 모든 경로에서 쿠키 전송
        refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7일

        res.addCookie(refreshCookie);

        // 6. 토큰 없이 프론트엔드로 리다이렉트
        // 프론트엔드는 페이지 로드 시 /auth/refresh API를 호출하여 쿠키를 주고 AccessToken을 받아야 함
        res.sendRedirect(redirectUri);
    }
}