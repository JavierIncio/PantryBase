package com.pantrybase.api.auth.repository;

import com.pantrybase.api.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String hash);
    void deleteByUserId(Long userId);
}
