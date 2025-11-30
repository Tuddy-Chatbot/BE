package io.github.tuddy.service;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tuddy.entity.user.RefreshToken;
import io.github.tuddy.repository.RefreshTokenRepository;
import io.github.tuddy.security.AuthUser;
import io.github.tuddy.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String issueRefreshToken(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        // 기존 토큰이 있다면 삭제 (단일 기기 로그인 정책일 경우. 다중 기기면 유지)
        refreshTokenRepository.deleteByUserId(user.getId());

        String token = jwtTokenProvider.createRefreshToken(auth);
        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .tokenValue(token)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(rt);
        return token;
    }

    @Transactional
    public String rotateRefreshToken(String oldRefreshToken, Authentication auth) {
        // 기존 토큰 검증 및 삭제 (DB 확인)
        RefreshToken rt = refreshTokenRepository.findByTokenValue(oldRefreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        refreshTokenRepository.delete(rt); // 기존 토큰 폐기 (Rotation)

        // 새 토큰 발급
        return issueRefreshToken(auth);
    }

    @Transactional
    public void deleteRefreshToken(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}