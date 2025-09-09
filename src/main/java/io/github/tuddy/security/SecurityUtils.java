package io.github.tuddy.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
  private SecurityUtils() {}
  public static Long requireUserId() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getPrincipal() == null) {
		throw new InsufficientAuthenticationException("unauthenticated");
	}
    Object p = a.getPrincipal();
    if (p instanceof AuthUser au) {
		return au.getId();
	}
    throw new InsufficientAuthenticationException("unauthenticated");
  }
}
