package com.pantrybase.api.auth.service;

import com.pantrybase.api.auth.domain.RefreshToken;
import com.pantrybase.api.auth.repository.RefreshTokenRepository;
import com.pantrybase.api.user.domain.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Service class for managing refresh tokens.
 */
@Service
public class TokenService {

    private final RefreshTokenRepository repository;

    public TokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Stores a new refresh token for the specified user.
     *
     * @param user       the user for whom the refresh token is being stored
     * @param rawToken   the raw refresh token to be hashed and stored
     * @param expiration the expiration time of the refresh token
     */
    public void storeRefreshToken(User user, String rawToken, Instant expiration) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(hash(rawToken));
        rt.setExpiresAt(expiration);
        rt.setRevoked(false);
        rt.setCreatedAt(Instant.now());

        repository.save(rt);
    }

    /**
     * Checks if a refresh token is valid.
     *
     * @param rawToken the raw refresh token to check
     * @return true if the token is valid, false otherwise
     */
    public boolean isRefreshTokenValid(String rawToken) {
        return repository.findByTokenHash(hash(rawToken))
                .map(rt -> !rt.isRevoked() && rt.getExpiresAt().isAfter(Instant.now()))
                .orElse(false);
    }

    /**
     * Revokes a refresh token, marking it as invalid.
     *
     * @param rawToken the raw refresh token to revoke
     */
    public void revokeRefreshToken(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(rt -> {
            rt.setRevoked(true);
            repository.save(rt);
        });
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
