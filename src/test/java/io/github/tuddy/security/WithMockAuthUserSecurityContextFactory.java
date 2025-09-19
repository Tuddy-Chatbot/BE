package io.github.tuddy.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockAuthUserSecurityContextFactory implements WithSecurityContextFactory<WithMockAuthUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockAuthUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        AuthUser principal = new AuthUser(
            annotation.id(),
            annotation.username(),
            "{noop}", // 테스트용이므로 비밀번호는 사용 안 함
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );

        context.setAuthentication(auth);
        return context;
    }
}