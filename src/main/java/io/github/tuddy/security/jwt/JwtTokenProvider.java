package io.github.tuddy.security.jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import io.github.tuddy.security.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpiration;
    private final long refreshExpiration;

    // 토큰 타입을 구분하기 위한 상수 정의
    private static final String TYPE_CLAIM = "typ";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    public JwtTokenProvider(@Value("${jwt.secret-key}") String secretKey,
                            @Value("${jwt.access.expiration}") long accessExpiration,
                            @Value("${jwt.refresh.expiration}") long refreshExpiration) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    // Access Token 생성
    public String createAccessToken(Authentication authentication) {
        // [수정 2] ACCESS 타입 지정
        return createToken(authentication, accessExpiration, TYPE_ACCESS);
    }

    // Refresh Token 생성
    public String createRefreshToken(Authentication authentication) {
        // REFRESH 타입 지정
        return createToken(authentication, refreshExpiration, TYPE_REFRESH);
    }

    // 내부 메서드에 type 파라미터 추가
    private String createToken(Authentication authentication, long expiration, String type) {
        AuthUser userPrincipal = (AuthUser) authentication.getPrincipal();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date validity = new Date(now + expiration);

        return Jwts.builder()
                .subject(userPrincipal.getUsername()) // loginId
                .claim("auth", authorities)
                .claim("uid", userPrincipal.getId())
                .claim(TYPE_CLAIM, type) // ★ 핵심: 토큰 용도(ACCESS/REFRESH) 기록
                .issuedAt(new Date(now))
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    // 토큰 파싱 및 인증 정보 추출
    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        Long userId = claims.get("uid", Long.class);

        // AuthUser 객체 생성
        AuthUser principal = new AuthUser(userId, claims.getSubject(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    // [추가 5] Access Token 전용 검증 메서드
    // API 요청 시 헤더에 들어온 토큰이 진짜 Access Token인지 확인합니다. (Refresh Token 차단용)
    public boolean validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 토큰 타입이 ACCESS가 아니면 유효하지 않음
            return TYPE_ACCESS.equals(claims.get(TYPE_CLAIM));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 일반 토큰 유효성 검증 (서명 및 만료 여부만 확인)
    // Refresh Token 검증이나 로그아웃 시 등에 사용
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 남은 유효 시간 계산
    public long getRemainingTime(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration().getTime() - new Date().getTime();
    }
}