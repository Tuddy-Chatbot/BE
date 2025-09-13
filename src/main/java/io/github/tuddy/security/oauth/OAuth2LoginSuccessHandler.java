package io.github.tuddy.security.oauth;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import io.github.tuddy.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
  private final UserAccountRepository users;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth) {
    // 세션 기반. 프론트와 연동 시 JWT로 변경 예정이면 여기서 토큰 발급하도록 교체
    // 마지막 로그인 기록
    // 이메일 또는 provider id로 갱신
    // 간단화를 위해 세션 유지 후 200으로 리다이렉트 없이 종료
    res.setStatus(HttpServletResponse.SC_OK);
  }
}
