package io.github.tuddy.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AuthUser extends User {
  private final Long id;
  public AuthUser(Long id, String username, String password, Collection<? extends GrantedAuthority> auth) {
    super(username, password, auth);
    this.id = id;
  }
  public Long getId() { return id; }
}
