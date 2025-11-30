package io.github.tuddy.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
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

    // 하드코딩이 아닌 application.properties의 값 주입
    @Value("${jwt.refresh.expiration}")
    private long refreshExpirationMs;

    @Transactional
    public String issueRefreshToken(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        refreshTokenRepository.deleteByUserId(user.getId());

        String token = jwtTokenProvider.createRefreshToken(auth);

        // 설정값 기반으로 만료시간 계산
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusNanos(refreshExpirationMs * 1_000_000); // ms -> nanos 변환

        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .tokenValue(token)
                .issuedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
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