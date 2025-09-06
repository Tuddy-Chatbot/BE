package io.github.tuddychatbot.entity.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="user_accounts",
  uniqueConstraints = {
    @UniqueConstraint(name="uk_user_login_id", columnNames="login_id"),
    @UniqueConstraint(name="uk_user_email", columnNames="email"),
    @UniqueConstraint(name="uk_user_provider_uid", columnNames={"provider","provider_user_id"})
})
public class UserAccount {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name="login_id", length=50)
  private String loginId; // 사용자가 말한 "ID"

  @Column(length=320)
  private String email;

  @Column(name="display_name", length=60, nullable=false)
  private String displayName;

  @Column(name="password_hash", length=100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(length=20, nullable=false)
  private AuthProvider provider;

  @Column(name="provider_user_id", length=100)
  private String providerUserId;

  @Enumerated(EnumType.STRING)
  @Column(length=20, nullable=false)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(length=20, nullable=false)
  private UserStatus status;

  private LocalDateTime lastLoginAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist void prePersist() {
	LocalDateTime now = LocalDateTime.now();
    this.createdAt = now; this.updatedAt = now;
    if (role == null) {
		role = UserRole.USER;
	}
    if (status == null) {
		status = UserStatus.ACTIVE;
	}
  }
  @PreUpdate void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
