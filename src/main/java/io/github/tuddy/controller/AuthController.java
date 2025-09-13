package io.github.tuddy.controller;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.dto.AuthMeResponse;
import io.github.tuddy.dto.LoginRequest;
import io.github.tuddy.dto.RegisterLocalRequest;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.repository.UserAccountRepository;
import io.github.tuddy.security.AuthUser;
import io.github.tuddy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;
  private final AuthenticationManager authManager;
  private final UserAccountRepository users;

  // SecurityContext를 세션에 명시적으로 저장하기 위한 Repository
  private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

  // 계정 중복 체크 -> 저장 성공 시 200, 201 응답, 세션 생성 x
  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody RegisterLocalRequest req) {
    var u = authService.registerLocal(req);
    return ResponseEntity.ok(new AuthMeResponse(u.getId(), u.getDisplayName(), u.getLoginId(), u.getEmail(),
      u.getProvider().name(), u.getRole().name()));
  }

  // JSESSIONID 쿠키로 인증 유지
  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
    var token = new UsernamePasswordAuthenticationToken(req.loginIdOrEmail(), req.password());
    token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    Authentication auth = authManager.authenticate(token);

    // last_login_at 갱신 로직 추가
    if (auth.getPrincipal() instanceof AuthUser) {
      AuthUser authUser = (AuthUser) auth.getPrincipal();
      users.findById(authUser.getId()).ifPresent(user -> {
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
      });
    }

    // 인증 정보를 SecurityContext에 설정
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);

    // SecurityContext를 세션에 명시적으로 저장
    securityContextRepository.saveContext(context, request, response);

    return ResponseEntity.ok().build();
  }

  // 미인증 시 401 오류
  @GetMapping("/me")
  public ResponseEntity<?> me() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return ResponseEntity.status(401).build();
    }

    Object principal = auth.getPrincipal();
    if (principal instanceof AuthUser) {
      AuthUser authUser = (AuthUser) principal;
      // DB에서 전체 사용자 정보를 조회하여 완전한 응답을 생성
      UserAccount user = users.findById(authUser.getId()).orElse(null);
      if (user == null) {
        return ResponseEntity.status(404).body("User not found");
      }
      return ResponseEntity.ok(new AuthMeResponse(
          user.getId(),
          user.getDisplayName(),
          user.getLoginId(),
          user.getEmail(),
          user.getProvider().name(),
          user.getRole().name()
      ));
    }

    return ResponseEntity.ok(auth.getName());
  }

  // 서버 세션 무효화 : CSRF 헤더 필요
  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) throws Exception {
    req.getSession().invalidate();
    return ResponseEntity.ok().build();
  }
}

