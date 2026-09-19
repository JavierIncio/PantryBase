package com.pantrybase.api.user;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.repository.RefreshTokenRepository;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.user.dto.UserResponse;
import com.pantrybase.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public static final String ME = "/api/users/me";

    private record Session(String accessToken) {
    }

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
        refreshTokenRepository.deleteAll();
    }

    @Test
    void me_withBearerToken_shouldReturn200() {
        Session session = registerSession();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<UserResponse> response =
                rest.exchange(ME, HttpMethod.GET, request, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        assertThat(response.getBody().username()).isEqualTo("JohnDoe");
        assertThat(response.getBody().email()).isEqualTo("john.doe@example.com");
    }

    @Test
    void me_withoutToken_shouldReturn401() {
        ResponseEntity<ErrorResponse> response =
                rest.getForEntity(ME, ErrorResponse.class);

        assertError(HttpStatus.UNAUTHORIZED, response, ME);
    }

    @Test
    void me_withInvalidToken_shouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.jwt.token");

        ResponseEntity<ErrorResponse> response =
                rest.exchange(ME, HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(HttpStatus.UNAUTHORIZED, response, ME);
    }

    private Session registerSession() {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "username", "JohnDoe",
                "email", "john.doe@example.com",
                "password", "password123"
        ));
        ResponseEntity<AuthResponse> response =
                rest.postForEntity("/api/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        return new Session(response.getBody().accessToken());
    }

    private void assertError(HttpStatus expectedStatus,
                             ResponseEntity<ErrorResponse> response,
                             String expectedPath) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.status()).isEqualTo(expectedStatus.value());
            assertThat(body.error()).isEqualTo(expectedStatus.getReasonPhrase());
            assertThat(body.message()).isNotBlank();
            assertThat(body.path()).isEqualTo(expectedPath);
            assertThat(body.timestamp()).isNotNull();
        });
    }
}
