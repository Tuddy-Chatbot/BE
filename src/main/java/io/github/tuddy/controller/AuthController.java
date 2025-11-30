package io.github.tuddy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.dto.AuthMeResponse;
import io.github.tuddy.dto.LoginRequest;
import io.github.tuddy.dto.RefreshTokenRequest;
import io.github.tuddy.dto.RegisterLocalRequest;
import io.github.tuddy.dto.TokenResponse;
import io.github.tuddy.repository.UserAccountRepository;
import io.github.tuddy.security.SecurityUtils;
import io.github.tuddy.security.jwt.JwtTokenProvider;
import io.github.tuddy.service.AuthService;
import io.github.tuddy.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "사용자 인증 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;
    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountRepository users;

    @Operation(summary = "로컬 계정 회원가입")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterLocalRequest req) {
        var u = authService.registerLocal(req);
        return ResponseEntity.ok(new AuthMeResponse(u.getId(), u.getDisplayName(), u.getLoginId(), u.getEmail(),
                u.getProvider().name(), u.getRole().name()));
    }

    @Operation(summary = "로그인", description = "성공 시 Access/Refresh Token 반환")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.loginIdOrEmail(), req.password())
        );

        String accessToken = jwtTokenProvider.createAccessToken(auth);
        String refreshToken = tokenService.issueRefreshToken(auth);

        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }

    @Operation(summary = "토큰 갱신 (Refresh)", description = "Refresh Token을 사용하여 Access Token 재발급 (RTR 적용)")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest req) {
        if (!jwtTokenProvider.validateToken(req.refreshToken())) {
            return ResponseEntity.status(401).build();
        }
        Authentication auth = jwtTokenProvider.getAuthentication(req.refreshToken());

        // Refresh Token Rotation: 기존 토큰 삭제 후 새 세트 발급
        String newRefreshToken = tokenService.rotateRefreshToken(req.refreshToken(), auth);
        String newAccessToken = jwtTokenProvider.createAccessToken(auth);

        return ResponseEntity.ok(new TokenResponse(newAccessToken, newRefreshToken));
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Long userId = SecurityUtils.requireUserId();
        var user = users.findById(userId).orElseThrow();
        return ResponseEntity.ok(new AuthMeResponse(user.getId(), user.getDisplayName(), user.getLoginId(),
                user.getEmail(), user.getProvider().name(), user.getRole().name()));
    }

    @Operation(summary = "로그아웃", description = "서버 DB에서 Refresh Token 삭제")
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        try {
            Long userId = SecurityUtils.requireUserId();
            tokenService.deleteRefreshToken(userId);
        } catch (Exception e) {
            // 이미 인증이 만료되었거나 없는 경우도 무시하고 OK
        }
        return ResponseEntity.ok().build();
    }
}