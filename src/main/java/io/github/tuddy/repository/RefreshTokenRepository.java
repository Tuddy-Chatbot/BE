package io.github.tuddy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tuddy.entity.user.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenValue(String tokenValue);
    void deleteByUserId(Long userId); // 로그아웃 시 전체 삭제 혹은 기기별 관리 시 로직 변경 가능
}