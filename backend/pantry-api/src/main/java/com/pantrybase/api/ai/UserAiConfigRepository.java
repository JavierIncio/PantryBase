package com.pantrybase.api.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAiConfigRepository extends JpaRepository<UserAiConfig, Long> {
    Optional<UserAiConfig> findByUserId(Long userId);
}