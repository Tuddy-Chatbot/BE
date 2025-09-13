package io.github.tuddy.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tuddy.dto.RegisterLocalRequest;
import io.github.tuddy.entity.user.AuthProvider;
import io.github.tuddy.entity.user.UserAccount;
import io.github.tuddy.entity.user.UserRole;
import io.github.tuddy.entity.user.UserStatus;
import io.github.tuddy.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class AuthService {
  private final UserAccountRepository users;
  private final PasswordEncoder encoder;

  @Transactional
  public UserAccount registerLocal(RegisterLocalRequest req) {
    if (!req.password().equals(req.passwordConfirm())) {
		throw new IllegalArgumentException("password_confirm_mismatch");
	}
    users.findByLoginId(req.loginId()).ifPresent(u -> { throw new IllegalStateException("login_id_in_use"); });
    users.findByEmail(req.email()).ifPresent(u -> { throw new IllegalStateException("email_in_use"); });

    UserAccount ua = UserAccount.builder()
    	    .loginId(req.loginId())
    	    .email(req.email())
    	    .displayName(req.displayName())
    	    .passwordHash(encoder.encode(req.password()))
    	    .provider(AuthProvider.LOCAL)
    	    .role(UserRole.USER)
    	    .status(UserStatus.ACTIVE)
    	    .build();

    	return users.save(ua);
  }
}
