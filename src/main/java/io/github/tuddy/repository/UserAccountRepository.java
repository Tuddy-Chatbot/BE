package io.github.tuddy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.user.AuthProvider;
import io.github.tuddy.entity.user.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByLoginId(String loginId);
  Optional<UserAccount> findByEmail(String email);
  Optional<UserAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
