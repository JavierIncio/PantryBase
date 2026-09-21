package com.pantrybase.api.common.ratelimit;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimitFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimitProperties props;

    @Test
    void login_exhaustedBucket_shouldReturn429() {
        registerUser(DEFAULT_USER);

        for (int request = 1; request <= props.capacity(); request++) {
            ResponseEntity<ErrorResponse> response = loginWith("wrongpassword");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertRateLimitHeaders(response, props.capacity() - request);
        }

        assertLimited(loginWith("wrongpassword"), LOGIN);
    }

    @Test
    void login_allowedRequests_shouldDecreaseRemainingHeader() {
        registerUser(DEFAULT_USER);

        for (int request = 1; request <= 3; request++) {
            ResponseEntity<ErrorResponse> response = loginWith("wrongpassword");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertRateLimitHeaders(response, props.capacity() - request);
        }
    }

    @Test
    void buckets_areIsolatedPerEndpoint() {
        Session session = registerSession();

        for (int request = 1; request <= props.capacity(); request++) {
            ResponseEntity<UserResponse> response = me(session);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertRateLimitHeaders(response, props.capacity() - request);
        }
        assertLimited(meFailed(session), ME);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + session.tokens().refreshToken());
        ResponseEntity<AuthResponse> refresh =
                rest.postForEntity(REFRESH, new HttpEntity<>(headers), AuthResponse.class);

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRateLimitHeaders(refresh, props.capacity() - 1);
    }

    @Test
    void authenticatedUser_hasOwnBucket() {
        Session session = registerSession();

        // Exhaust the bucket for the authenticated user
        for (int request = 1; request <= props.capacity(); request++) {
            ResponseEntity<UserResponse> user = me(session);
            assertThat(user.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertRateLimitHeaders(user, props.capacity() - request);
        }
        assertLimited(meFailed(session), ME);

        // Ensure that anonymous requests are not affected by the authenticated user's bucket
        for (int request = 1; request <= props.capacity(); request++) {
            ResponseEntity<ErrorResponse> response = meAnonymous();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertRateLimitHeaders(response, props.capacity() - request);
        }
        assertLimited(meAnonymous(), ME);
    }

    @Test
    void excludedPaths_areNotLimited() {
        for (int request = 1; request <= props.capacity() + 5; request++) {
            ResponseEntity<Void> response = rest.exchange("/actuator/health", HttpMethod.GET, null, Void.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().containsHeader(RateLimitFilter.RATE_LIMIT_LIMIT_HEADER)).isFalse();
            assertThat(response.getHeaders().containsHeader(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER)).isFalse();
        }
    }


    private ResponseEntity<ErrorResponse> loginWith(String password) {
        return rest.postForEntity(LOGIN, credentials("JohnDoe", password), ErrorResponse.class);
    }

    private ResponseEntity<UserResponse> me(Session session) {
        return rest.exchange(ME, HttpMethod.GET, authenticated(session), UserResponse.class);
    }

    private ResponseEntity<ErrorResponse> meFailed(Session session) {
        return rest.exchange(ME, HttpMethod.GET, authenticated(session), ErrorResponse.class);
    }

    private HttpEntity<Void> authenticated(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.tokens().accessToken());
        return new HttpEntity<>(headers);
    }

    private ResponseEntity<ErrorResponse> meAnonymous() {
        return rest.getForEntity(ME, ErrorResponse.class);
    }

    private void assertRateLimitHeaders(ResponseEntity<?> response, long remaining) {
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst(RateLimitFilter.RATE_LIMIT_LIMIT_HEADER)).isEqualTo(String.valueOf(props.capacity()));
        assertThat(headers.getFirst(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER)).isEqualTo(String.valueOf(remaining));
    }

    private void assertLimited(ResponseEntity<ErrorResponse> response, String path) {
        assertError(response, HttpStatus.TOO_MANY_REQUESTS, path);
        assertRateLimitHeaders(response, 0);
    }
}