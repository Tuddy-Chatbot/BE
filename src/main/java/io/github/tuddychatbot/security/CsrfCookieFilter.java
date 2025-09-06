package io.github.tuddychatbot.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CsrfCookieFilter extends OncePerRequestFilter {
 @Override
 protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
         throws ServletException, IOException {

     CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
     if (token != null) {
         String value = token.getToken();
         Cookie existing = WebUtils.getCookie(request, "XSRF-TOKEN");
         if (existing == null || (value != null && !value.equals(existing.getValue()))) {
             Cookie cookie = new Cookie("XSRF-TOKEN", value);
             cookie.setPath("/");
             cookie.setHttpOnly(false);           // SPA에서 읽어 헤더로 보냄
             cookie.setSecure(request.isSecure());
             response.addCookie(cookie);
         }
     }
     chain.doFilter(request, response);
 }
}
