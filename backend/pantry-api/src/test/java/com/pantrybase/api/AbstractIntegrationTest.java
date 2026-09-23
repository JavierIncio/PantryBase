package com.pantrybase.api;

import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.repository.RefreshTokenRepository;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.user.repository.UserPreferencesRepository;
import com.pantrybase.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
public abstract class AbstractIntegrationTest {
    protected static final String REGISTER = "/api/auth/register";
    protected static final String LOGIN = "/api/auth/login";
    protected static final String REFRESH = "/api/auth/refresh";
    protected static final String LOGOUT = "/api/auth/logout";
    protected static final String ME = "/api/users/me";

    protected static final Map<String, String> DEFAULT_USER = Map.of(
            "username", "JohnDoe",
            "email", "john.doe@example.com",
            "password", "password123"
    );

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected StringRedisTemplate redisTemplate;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected UserPreferencesRepository preferencesRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        preferencesRepository.deleteAll();

        Set<String> keys = redisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    protected record Session(AuthResponse tokens, String refreshTokenCookie) {
    }

    protected ResponseEntity<AuthResponse> registerUser(Map<String, String> user) {
        ResponseEntity<AuthResponse> response =
                rest.postForEntity(REGISTER, new HttpEntity<>(user), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        return response;
    }

    protected HttpHeaders authenticate() {
        Session session = registerSession();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.tokens().accessToken());

        return headers;
    }

    protected Session registerSession() {
        ResponseEntity<AuthResponse> response = registerUser(DEFAULT_USER);

        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .contains("refresh_token=")
                .contains("HttpOnly");

        String refreshToken = parseCookieAttributes(setCookie).get("refresh_token");
        assertThat(refreshToken).isNotBlank();

        return new Session(response.getBody(), refreshToken);
    }

    protected HttpEntity<Map<String, String>> credentials(String loginMethod, String password) {
        return new HttpEntity<>(Map.of("loginMethod", loginMethod, "password", password));
    }

    protected void assertError(ResponseEntity<ErrorResponse> response,
                               HttpStatus expectedStatus,
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

    protected Map<String, String> parseCookieAttributes(String setCookie) {
        assertThat(setCookie).isNotBlank();

        return Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> parts.length > 1 ? parts[1] : "",
                        (existing, replacement) -> existing));
    }

}
