package com.pantrybase.api.auth;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.dto.RegisterRequest;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthControllerIntegrationTest extends AbstractIntegrationTest {

        private static final Long ACCESS_TOKEN_TTL_SECONDS = 900L;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
        login("JohnDoe");
    }

    @Test
    void login_withEmail_shouldReturn200() {
        registerSession();
        login("john.doe@example.com");
    }

    @Test
    void login_wrongPassword_shouldReturn401() {
        registerSession();

        ResponseEntity<ErrorResponse> response =
                rest.postForEntity(LOGIN, credentials("JohnDoe", "wrongpassword"), ErrorResponse.class);

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

    private void login(String identifier) {
        ResponseEntity<AuthResponse> response =
                rest.postForEntity(LOGIN, credentials(identifier, "password123"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isEqualTo(ACCESS_TOKEN_TTL_SECONDS);

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("refresh_token=").contains("HttpOnly");
    }
}
