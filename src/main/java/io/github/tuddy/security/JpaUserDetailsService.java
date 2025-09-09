package io.github.tuddy.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.tuddy.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {
  private final UserAccountRepository users;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var u = users.findByLoginId(username)
        .or(() -> users.findByEmail(username))
        .orElseThrow(() -> new UsernameNotFoundException("user_not_found"));

    var auth = java.util.List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
    var pw = u.getPasswordHash() != null ? u.getPasswordHash() : "{noop}";
    var loginName = u.getLoginId() != null ? u.getLoginId() : u.getEmail();

    return new AuthUser(u.getId(), loginName, pw, auth);
  }
}
