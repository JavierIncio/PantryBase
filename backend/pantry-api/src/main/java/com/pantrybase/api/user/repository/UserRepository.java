package com.pantrybase.api.user.repository;

import com.pantrybase.api.user.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameOrEmail(String username, String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<User> findByGoogleId(String googleId);
}
