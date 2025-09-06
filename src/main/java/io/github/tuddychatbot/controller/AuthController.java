package io.github.tuddychatbot.controller;

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

import io.github.tuddychatbot.dto.AuthMeResponse;
import io.github.tuddychatbot.dto.LoginRequest;
import io.github.tuddychatbot.dto.RegisterLocalRequest;
import io.github.tuddychatbot.service.AuthService;
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

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody RegisterLocalRequest req) {
    var u = authService.registerLocal(req);
    return ResponseEntity.ok(new AuthMeResponse(u.getId(), u.getDisplayName(), u.getLoginId(), u.getEmail(),
      u.getProvider().name(), u.getRole().name()));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest servletReq) {
    var token = new UsernamePasswordAuthenticationToken(req.loginIdOrEmail(), req.password());
    token.setDetails(new WebAuthenticationDetailsSource().buildDetails(servletReq));
    Authentication auth = authManager.authenticate(token);
    SecurityContextHolder.getContext().setAuthentication(auth); // 세션에 저장
    return ResponseEntity.ok().build();
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(Authentication auth) {
    if (auth == null) {
		return ResponseEntity.ok().build();
	}
    var principal = auth.getName();
    return ResponseEntity.ok(principal);
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) throws Exception {
    req.getSession().invalidate();
    return ResponseEntity.ok().build();
  }
}
