package com.pantrybase.api.auth.service;

import com.pantrybase.api.auth.domain.PasswordResetToken;
import com.pantrybase.api.auth.mail.PasswordResetMailer;
import com.pantrybase.api.auth.repository.PasswordResetTokenRepository;
import com.pantrybase.api.auth.repository.RefreshTokenRepository;
import com.pantrybase.api.common.exception.InvalidPasswordResetTokenException;
import com.pantrybase.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Service for handling password reset requests and operations.
 */
@Service
public class PasswordResetService {
    private static final int TTL_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordResetMailer mailer;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String baseUrl;

    public PasswordResetService(UserRepository userRepo,
                                PasswordResetTokenRepository tokenRepo,
                                RefreshTokenRepository refreshTokenRepo,
                                PasswordResetMailer mailer,
                                PasswordEncoder encoder,
                                Clock clock,
                                @Value("${app.frontend-base-url}") String baseUrl) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.mailer = mailer;
        this.encoder = encoder;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    /**
     * Requests a password reset for the user identified by the given login method.
     *
     * <p>If a user with the given username or email exists, a password reset token
     * is generated and stored in the database, and a password reset email is sent
     * to the user's email address. If no such user exists, this method does nothing.</p>
     *
     * @param loginMethod the username or email of the user requesting a password reset
     */
    public void requestReset(String loginMethod) {
        userRepo.findByUsernameOrEmail(loginMethod, loginMethod).ifPresent(user -> {
            String token = generateResetToken();
            Instant expiresAt = clock.instant().plusSeconds(TTL_MINUTES * 60);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUserId(user.getId());
            resetToken.setTokenHash(hash(token));
            resetToken.setExpiresAt(expiresAt);
            tokenRepo.save(resetToken);

            String resetLink = baseUrl + "/auth/reset-password?token=" + token;
            mailer.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
        });
    }

    /**
     * Resets the user's password using a valid password reset token.
     *
     * <p>The reset token must exist and must not be expired. Once the password
     * has been changed, all existing refresh tokens for the user are revoked
     * and the password reset token is deleted so that it cannot be reused.</p>
     *
     * <p>Expired reset tokens are not deleted here. Their cleanup should be
     * handled separately by a scheduled cleanup job.</p>
     *
     * @param token the raw password reset token received by the user
     * @param newPassword the new password to set
     * @throws InvalidPasswordResetTokenException if the token does not exist,
     * is expired, or its user does not exist
     */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepo.findByTokenHash(hash(token))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (resetToken.getExpiresAt().isBefore(clock.instant()))
            throw new InvalidPasswordResetTokenException();


        userRepo.findById(resetToken.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new)
                .setPasswordHash(encoder.encode(newPassword));

        refreshTokenRepo.deleteByUserId(resetToken.getUserId());
        tokenRepo.delete(resetToken);
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
