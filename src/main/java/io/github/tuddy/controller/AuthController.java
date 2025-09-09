package io.github.tuddy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.dto.AuthMeResponse;
import io.github.tuddy.dto.LoginRequest;
import io.github.tuddy.dto.RegisterLocalRequest;
import io.github.tuddy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;
  private final AuthenticationManager authManager;

  // 계정 중복 체크 -> 저장 성공 시 200, 201 응답, 세션 생성 x
  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody RegisterLocalRequest req) {
    var u = authService.registerLocal(req);
    return ResponseEntity.ok(new AuthMeResponse(u.getId(), u.getDisplayName(), u.getLoginId(), u.getEmail(),
      u.getProvider().name(), u.getRole().name()));
  }

  // JSESSIONID 쿠키로 인증 유지
  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest servletReq) {
    var token = new UsernamePasswordAuthenticationToken(req.loginIdOrEmail(), req.password());
    token.setDetails(new WebAuthenticationDetailsSource().buildDetails(servletReq));
    Authentication auth = authManager.authenticate(token);
    SecurityContextHolder.getContext().setAuthentication(auth); // 세션에 저장
    return ResponseEntity.ok().build();
  }

  // 미인증 시 401 오류
  @GetMapping("/me")
  public ResponseEntity<?> me(Authentication auth) {
    if (auth == null) {
		return ResponseEntity.ok().build();
	}
    var principal = auth.getName();
    return ResponseEntity.ok(principal);
  }

  // 서버 세션 무효화 : CSRF 헤더 필요
  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) throws Exception {
    req.getSession().invalidate();
    return ResponseEntity.ok().build();
  }
}
