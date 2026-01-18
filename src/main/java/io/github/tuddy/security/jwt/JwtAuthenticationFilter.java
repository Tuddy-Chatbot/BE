package io.github.tuddy.security.jwt;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 변수명이 tokenProvider 임을 확인
    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // try-catch 블록 추가: 토큰 검증 중 에러가 나도 401을 바로 뱉지 않고 다음 필터로 넘김
        try {
            String token = resolveToken(request);

            // validateAccessToken 사용 (Access Token인지까지 확인)
            if (StringUtils.hasText(token) && tokenProvider.validateAccessToken(token)) {
                Authentication auth = tokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            // 토큰이 잘못되었거나 만료된 경우, 에러 로그만 찍고 SecurityContext는 비워둔 채 진행
            // OncePerRequestFilter가 제공하는 logger 사용
            logger.error("Could not set user authentication in security context", e);
        }

        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}