package com.pantrybase.api.auth;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.auth.domain.PasswordResetToken;
import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.dto.RegisterRequest;
import com.pantrybase.api.auth.mail.PasswordResetMailer;
import com.pantrybase.api.auth.repository.PasswordResetTokenRepository;
import com.pantrybase.api.auth.service.AuthService;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class ResetMailerConfig {
        @Bean
        @Primary
        PasswordResetMailer capturingPasswordResetMailer() { return new CapturingPasswordResetMailer(); }
    }

    static class CapturingPasswordResetMailer implements PasswordResetMailer {
        final List<SentEmail> sent = new ArrayList<>();
        record SentEmail(String to, String resetLink) {}
        @Override public void sendPasswordResetEmail(String to, String name, String resetLink) {
            sent.add(new SentEmail(to, resetLink));
        }
    }

    private static final Long ACCESS_TOKEN_TTL_SECONDS = 900L;
    private static final String CHANGE_PASSWORD = "/api/auth/password";

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;
    @Autowired
    private CapturingPasswordResetMailer mailer;
    @Autowired
    private PasswordResetTokenRepository passResetTokenRepo;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @BeforeEach
    void clearCapturedMails() {
        mailer.sent.clear();
    }

    @Test
    void register_validRequest_shouldCreateUserAndReturnTokens() {
        registerSession();

        User user = userRepository.findByUsernameOrEmail("JohnDoe", "john.doe@example.com")
                .orElseThrow();

        assertThat(user.getPasswordHash())
                .isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", user.getPasswordHash()))
                .isTrue();
    }

    @Test
    void register_duplicateUsername_shouldThrowException() {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "username", "JohnDoe",
                "email", "j.doe@test.com",
                "password", "password123"
        ));
        rest.postForEntity(REGISTER, request, AuthResponse.class);

        ResponseEntity<ErrorResponse> response =
                rest.postForEntity(REGISTER, new HttpEntity<>(Map.of(
                        "username", "JohnDoe",
                        "email", "j.doe2@test.com",
                        "password", "password123"
                )), ErrorResponse.class);

        // Ensure that the second user was not created
        assertThat(userRepository.existsByEmail("j.doe2@test.com")).isFalse();

        assertError(response, HttpStatus.CONFLICT, REGISTER);
    }

    @Test
    void register_duplicateEmail_shouldThrowException() {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "username", "JohnDoe",
                "email", "j.doe@test.com",
                "password", "password123"
        ));
        rest.postForEntity(REGISTER, request, AuthResponse.class);

        ResponseEntity<ErrorResponse> response =
                rest.postForEntity(REGISTER, new HttpEntity<>(Map.of(
                        "username", "JohnDoe2",
                        "email", "j.doe@test.com",
                        "password", "password123"
                )), ErrorResponse.class);

        // Ensure that the second user was not created
        assertThat(userRepository.existsByUsername("JohnDoe2")).isFalse();

        assertError(response, HttpStatus.CONFLICT, REGISTER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegisterPayloads")
    void register_invalidPayload_shouldReturn400(String testCase, String username, String email,
                                                 String password, String firstName, String lastName) {
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                REGISTER, new RegisterRequest(username, email, password, firstName, lastName), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, REGISTER);
        assertThat(userRepository.findByUsernameOrEmail(username, email))
                .isEmpty();
    }

    @Test
    void login_withUsername_shouldReturn200() {
        registerSession();
        login("JohnDoe", "password123");
    }

    @Test
    void login_withEmail_shouldReturn200() {
        registerSession();
        login("john.doe@example.com", "password123");
    }

    @Test
    void login_wrongPassword_shouldReturn401() {
        registerSession();

        ResponseEntity<ErrorResponse> response =
                rest.postForEntity(LOGIN, credentials("JohnDoe", "wrongpassword"), ErrorResponse.class);

        assertError(response, HttpStatus.UNAUTHORIZED, LOGIN);
    }

    @Test
    void login_socialAccount_withAnyPassword_shouldBeUnauthorized() {
        User created = authService.linkOrCreateOAuthUser(Map.of(
                "sub", "g-id", "email", "new.user@test.com"));
        assertThat(created.getPasswordHash()).isNull();

        ResponseEntity<ErrorResponse> response =
                rest.postForEntity(LOGIN, credentials("new.user@test.com", "anypassword"), ErrorResponse.class);
        assertError(response, HttpStatus.UNAUTHORIZED, LOGIN);
    }

    @Test
    void refreshToken_validRequest_shouldReturnNewAccessToken() {
        Session session = registerSession();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + session.refreshTokenCookie());

        ResponseEntity<AuthResponse> response =
                rest.postForEntity(REFRESH, new HttpEntity<>(headers), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().accessToken()).isNotEqualTo(session.tokens().accessToken());
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotEqualTo(session.tokens().refreshToken());
        assertThat(response.getBody().expiresIn()).isEqualTo(ACCESS_TOKEN_TTL_SECONDS);

        ResponseEntity<ErrorResponse> reuseResponse =
                rest.postForEntity(REFRESH, new HttpEntity<>(headers), ErrorResponse.class);
        assertError(reuseResponse, HttpStatus.UNAUTHORIZED, REFRESH);
    }

    @Test
    void refreshToken_invalidToken_shouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=invalidtoken");

        ResponseEntity<ErrorResponse> response =
                rest.postForEntity(REFRESH, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(response, HttpStatus.UNAUTHORIZED, REFRESH);
    }

    @Test
    void refreshToken_missingCookie_shouldReturn401() {
        ResponseEntity<ErrorResponse> response =
                rest.postForEntity(REFRESH, new HttpEntity<>(new HttpHeaders()), ErrorResponse.class);

        assertError(response, HttpStatus.UNAUTHORIZED, REFRESH);
    }

    @Test
    void logout_shouldInvalidateRefreshToken() {
        Session session = registerSession();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + session.refreshTokenCookie());

        ResponseEntity<Void> response =
                rest.postForEntity(LOGOUT, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, String> attrs = parseCookieAttributes(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        assertThat(attrs)
                .containsEntry("refresh_token", "")
                .containsEntry("Max-Age", "0")
                .containsEntry("Path", "/api/auth")
                .containsEntry("HttpOnly", "");

        ResponseEntity<ErrorResponse> refreshResponse =
                rest.postForEntity(REFRESH, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(refreshResponse, HttpStatus.UNAUTHORIZED, REFRESH);
    }

    @Test
    void logout_missingCookie_shouldReturn204() {
        ResponseEntity<Void> response =
                rest.postForEntity(LOGOUT, new HttpEntity<>(new HttpHeaders()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void linkOAuth_withCustomUsername_shouldLinkExistingUserAndKeepTheUsername() {
        registerSession();

        User linked = authService.linkOrCreateOAuthUser(Map.of(
                "sub", "g-id",
                "email", DEFAULT_USER.get("email")
        ));

        assertThat(linked.getUsername()).isEqualTo(DEFAULT_USER.get("username"));
        assertThat(linked.getGoogleId()).isEqualTo("g-id");

        User persisted = userRepository.findByGoogleId("g-id").orElseThrow();
        assertThat(persisted.getUsername()).isEqualTo(DEFAULT_USER.get("username"));
    }

    @Test
    void createOAuth_withFreePrefix_shouldDeriveUsernameFromEmail() {
        User created = authService.linkOrCreateOAuthUser(Map.of(
                "sub", "g-id", "email", "new.user@test.com"));

        assertThat(created.getUsername()).isEqualTo("new.user");
    }

    @Test
    void createOAuth_withOccupiedPrefix_shouldDeriveSuffixedUniqueUsernameFromEmail() {
        registerUser(Map.of(
                "username", "new.user",
                "email", "local@test.com",
                "password", "password123"));

        User created = authService.linkOrCreateOAuthUser(Map.of(
                "sub", "g-id", "email", "new.user@test.com"));

        assertThat(created.getUsername()).isEqualTo("new.user1");
    }

    @Test
    void changePassword_validRequest_shouldUpdatePasswordAndRevokeTokens() {
        Session session = registerSession();
        String oldRefreshToken = session.tokens().refreshToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.tokens().accessToken());
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "currentPassword", "password123",
                "newPassword", "newpassword456"
        ), headers);

        ResponseEntity<Void> response = rest.exchange(
                CHANGE_PASSWORD, HttpMethod.PUT, request, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // The old refresh token is dead: refreshing with it must fail.
        HttpHeaders refreshOldHeaders = new HttpHeaders();
        refreshOldHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + oldRefreshToken);
        ResponseEntity<ErrorResponse> refreshOldResponse =
                rest.exchange(REFRESH, HttpMethod.POST, new HttpEntity<>(refreshOldHeaders), ErrorResponse.class);
        assertThat(refreshOldResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The stored hash matches only the new password.
        User user = userRepository
                .findByUsernameOrEmail("JohnDoe", "john.doe@example.com")
                .orElseThrow();
        assertThat(passwordEncoder.matches("newpassword456", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isFalse();

        // The old password no longer logs in; the new one does.
        ResponseEntity<ErrorResponse> oldLogin = rest.postForEntity(
                LOGIN, credentials("JohnDoe", "password123"), ErrorResponse.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        login("JohnDoe", "newpassword456");
    }

    @Test
    void changePassword_withWrongCurrentPassword_shouldReturn400() {
        HttpHeaders headers = authenticate();
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "currentPassword", "wrongpassword",
                "newPassword", "newpassword456"
        ), headers);

        ResponseEntity<ErrorResponse> response = rest.exchange(
                CHANGE_PASSWORD, HttpMethod.PUT, request, ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, CHANGE_PASSWORD);
    }

    @Test
    void changePassword_withBlankCurrentPassword_shouldReturn400() {
        HttpHeaders headers = authenticate();
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "currentPassword", "",
                "newPassword", "newpassword456"
        ), headers);

        ResponseEntity<ErrorResponse> response = rest.exchange(
                CHANGE_PASSWORD, HttpMethod.PUT, request, ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, CHANGE_PASSWORD);
    }

    @Test
    void changePassword_socialAccount_withCurrentPassword_shouldReturn409() {
        User socialUser = authService.linkOrCreateOAuthUser(Map.of(
                "sub", "g-id", "email", "new.user@test.com"));

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "currentPassword", "password123",
                "newPassword", "newpassword456"
        ), bearerFor(socialUser));

        ResponseEntity<ErrorResponse> response = rest.exchange(
                CHANGE_PASSWORD, HttpMethod.PUT, request, ErrorResponse.class);

        assertError(response, HttpStatus.CONFLICT, CHANGE_PASSWORD);
    }

    @Test
    void changePassword_socialAccount_withoutCurrentPassword_shouldEstablishPassword() {
        User socialUser = authService.linkOrCreateOAuthUser(Map.of(
                "sub", "g-id", "email", "new.user@test.com"));

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "currentPassword", "",
                "newPassword", "newpassword456"
        ), bearerFor(socialUser));

        ResponseEntity<Void> response = rest.exchange(
                CHANGE_PASSWORD, HttpMethod.PUT, request, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // The social account now has a local credential.
        login("new.user@test.com", "newpassword456");
    }

    @Test
    void changePassword_unauthenticated_shouldReturn401() {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "currentPassword", "password123",
                "newPassword", "newpassword456"
        ));

        ResponseEntity<ErrorResponse> response = rest.exchange(
                CHANGE_PASSWORD, HttpMethod.PUT, request, ErrorResponse.class);

        assertError(response, HttpStatus.UNAUTHORIZED, CHANGE_PASSWORD);
    }

    @Test
    void requestReset_existingUser_shouldReturn204AndSendEmail() throws NoSuchAlgorithmException {
        registerSession();

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "loginMethod", DEFAULT_USER.get("username")));

        ResponseEntity<Void> response = rest.postForEntity(
                "/api/auth/password-reset-token", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(mailer.sent).hasSize(1);

        CapturingPasswordResetMailer.SentEmail sentEmail = mailer.sent.getFirst();

        assertThat(sentEmail.to()).isEqualTo(DEFAULT_USER.get("email"));

        String rawToken = extractToken(sentEmail.resetLink());

        PasswordResetToken token = passResetTokenRepo
                .findByTokenHash(sha256Hex(rawToken))
                .orElseThrow();

        assertThat(token.getExpiresAt()).isAfter(Instant.now());
        assertThat(sentEmail.resetLink()).isEqualTo(frontendBaseUrl + "/auth/reset-password?token=" + rawToken);
    }

    @Test
    void requestReset_unknownUser_shouldReturn204AndSendNoEmail() {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "loginMethod", "ghost"));

        ResponseEntity<Void> response = rest.postForEntity(
                "/api/auth/password-reset-token", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void reset_validToken_shouldUpdatePasswordAndRevokeSessions() {
        Session session = registerSession();
        String oldRefreshToken = session.tokens().refreshToken();

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "loginMethod", DEFAULT_USER.get("username")));

        rest.postForEntity("/api/auth/password-reset-token", request, Void.class);

        CapturingPasswordResetMailer.SentEmail sentEmail = mailer.sent.getFirst();
        String rawToken = extractToken(sentEmail.resetLink());

        HttpEntity<Map<String, String>> resetRequest = new HttpEntity<>(Map.of(
                "token", rawToken,
                "newPassword", "newpassword456"));

        ResponseEntity<Void> resetResponse = rest.postForEntity(
                "/api/auth/password-reset", resetRequest, Void.class);

        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ErrorResponse> oldLogin = rest.postForEntity(
                LOGIN, credentials(DEFAULT_USER.get("username"), "password123"), ErrorResponse.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        login(DEFAULT_USER.get("username"), "newpassword456");

        HttpHeaders refreshOldHeaders = new HttpHeaders();
        refreshOldHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + oldRefreshToken);
        ResponseEntity<ErrorResponse> refreshOld = rest.exchange(
                REFRESH, HttpMethod.POST, new HttpEntity<>(refreshOldHeaders), ErrorResponse.class);
        assertThat(refreshOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reset_invalidToken_shouldReturn400() {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "token", "f".repeat(64),
                "newPassword", "newpassword456"));

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/api/auth/password-reset", request, ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, "/api/auth/password-reset");
    }

    @Test
    void reset_expiredToken_shouldReturn400() throws NoSuchAlgorithmException {
        registerSession();
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "loginMethod", DEFAULT_USER.get("username")));
        ResponseEntity<Void> response = rest.postForEntity(
                "/api/auth/password-reset-token", request, Void.class);

        CapturingPasswordResetMailer.SentEmail sentEmail = mailer.sent.getFirst();
        String rawToken = extractToken(sentEmail.resetLink());

        PasswordResetToken row = passResetTokenRepo
                .findByTokenHash(sha256Hex(rawToken)).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        passResetTokenRepo.save(row);

        HttpEntity<Map<String, String>> resetRequest = new HttpEntity<>(Map.of(
                "token", rawToken,
                "newPassword", "newpassword456"));

        ResponseEntity<ErrorResponse> resetResponse = rest.postForEntity(
                "/api/auth/password-reset", resetRequest, ErrorResponse.class);

        assertError(resetResponse, HttpStatus.BAD_REQUEST, "/api/auth/password-reset");
    }

    @Test
    void reset_validToken_shouldBeSingleUse() {
        registerSession();
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "loginMethod", DEFAULT_USER.get("username")));
        ResponseEntity<Void> response = rest.postForEntity(
                "/api/auth/password-reset-token", request, Void.class);

        CapturingPasswordResetMailer.SentEmail sentEmail = mailer.sent.getFirst();
        String rawToken = extractToken(sentEmail.resetLink());

        HttpEntity<Map<String, String>> resetRequest = new HttpEntity<>(Map.of(
                "token", rawToken,
                "newPassword", "newpassword456"));

        ResponseEntity<Void> resetResponse = rest.postForEntity(
                "/api/auth/password-reset", resetRequest, Void.class);
        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ErrorResponse> reuseResponse = rest.postForEntity(
                "/api/auth/password-reset", resetRequest, ErrorResponse.class);
        assertError(reuseResponse, HttpStatus.BAD_REQUEST, "/api/auth/password-reset");
    }

    /**
     * Builds bearer headers for any user (also social ones, which cannot log in
     * through the password flow because they have no local credential yet).
     */
    private HttpHeaders bearerFor(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authService.issueTokens(user).accessToken());
        return headers;
    }

    private static Stream<Arguments> invalidRegisterPayloads() {
        return Stream.of(
                Arguments.of("username too short", "ab", "ok@test.com", "password123", null, null),
                Arguments.of("username too long", "x".repeat(21), "ok@test.com", "password123", null, null),
                Arguments.of("username blank", "", "ok@test.com", "password123", null, null),
                Arguments.of("email invalid", "test_ok", "invalid-email", "password123", null, null),
                Arguments.of("email blank", "test_ok", "", "password123", null, null),
                Arguments.of("password too short", "test_user", "ok@test.com", "pass123", null, null),
                Arguments.of("password too long", "test_user", "ok@test.com", "x".repeat(73), null, null),
                Arguments.of("password blank", "test_user", "ok@test.com", "", null, null),
                Arguments.of("first name too long", "test_user", "ok@test.com", "password123", "x".repeat(51), null),
                Arguments.of("last name too long", "test_user", "ok@test.com", "password123", null, "x".repeat(51))
        );
    }

    private void login(String identifier, String password) {
        ResponseEntity<AuthResponse> response =
                rest.postForEntity(LOGIN, credentials(identifier, password), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isEqualTo(ACCESS_TOKEN_TTL_SECONDS);

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("refresh_token=").contains("HttpOnly");
    }

    private static String sha256Hex(String raw) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static String extractToken(String resetLink) {
        return resetLink.substring(resetLink.indexOf("token=") + 6);
    }
}
