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

        // Login has no authenticated user yet, so it is limited by the strict IP policy.
        long capacity = strictLoginCapacity();
        for (int request = 1; request <= capacity; request++) {
            ResponseEntity<ErrorResponse> response = loginWith("wrongpassword");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertRateLimitHeaders(response, capacity, capacity - request);
        }

        assertLimited(loginWith("wrongpassword"), LOGIN);
    }

    @Test
    void login_allowedRequests_shouldDecreaseRemainingHeader() {
        registerUser(DEFAULT_USER);

        long capacity = strictLoginCapacity();
        for (int request = 1; request <= 3; request++) {
            ResponseEntity<ErrorResponse> response = loginWith("wrongpassword");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertRateLimitHeaders(response, capacity, capacity - request);
        }
    }

    @Test
    void buckets_areIsolatedPerEndpoint() {
        Session session = registerSession();

        for (int request = 1; request <= props.capacity(); request++) {
            ResponseEntity<UserResponse> response = me(session);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertRateLimitHeaders(response, props.capacity(), props.capacity() - request);
        }
        assertLimited(meFailed(session), ME);

        // The refresh carries the session cookie but no bearer token, so it lands on
        // the anonymous tier: its generous default capacity, not the user's bucket.
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + session.tokens().refreshToken());
        ResponseEntity<AuthResponse> refresh =
                rest.postForEntity(REFRESH, new HttpEntity<>(headers), AuthResponse.class);

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRateLimitHeaders(refresh, props.anonymous().capacity(), props.anonymous().capacity() - 1);
    }

    @Test
    void authenticatedUser_hasOwnBucket() {
        Session session = registerSession();

        // Exhaust the bucket for the authenticated user
        for (int request = 1; request <= props.capacity(); request++) {
            ResponseEntity<UserResponse> user = me(session);
            assertThat(user.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertRateLimitHeaders(user, props.capacity(), props.capacity() - request);
        }
        assertLimited(meFailed(session), ME);

        // Ensure that anonymous requests are not affected by the authenticated user's bucket
        long anonymousCapacity = props.anonymous().capacity();
        for (int request = 1; request <= anonymousCapacity; request++) {
            ResponseEntity<ErrorResponse> response = meAnonymous();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertRateLimitHeaders(response, anonymousCapacity, anonymousCapacity - request);
        }
        assertLimited(meAnonymous(), ME);
    }

    @Test
    void authenticatedRequests_doNotConsumeTheIpBucket() {
        Session session = registerSession();

        for (int request = 1; request <= props.capacity(); request++) {
            assertThat(me(session).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertLimited(meFailed(session), ME);

        // The user exhausted their own bucket; the IP bucket behind it must still be full.
        ResponseEntity<ErrorResponse> anonymous = meAnonymous();
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertRateLimitHeaders(anonymous,
                props.anonymous().capacity(), props.anonymous().capacity() - 1);
    }

    @Test
    void limitHeader_reflectsTheAppliedPolicy() {
        registerUser(DEFAULT_USER);

        ResponseEntity<ErrorResponse> login = loginWith("wrongpassword");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertRateLimitHeaders(login, strictLoginCapacity(), strictLoginCapacity() - 1);

        ResponseEntity<ErrorResponse> anonymous = meAnonymous();
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertRateLimitHeaders(anonymous, props.anonymous().capacity(), props.anonymous().capacity() - 1);
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

    private long strictLoginCapacity() {
        return props.anonymous().strictPaths().stream()
                .filter(strict -> strict.paths().contains(LOGIN))
                .findFirst()
                .orElseThrow()
                .capacity();
    }

    private void assertRateLimitHeaders(ResponseEntity<?> response, long expectedLimit, long remaining) {
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst(RateLimitFilter.RATE_LIMIT_LIMIT_HEADER)).isEqualTo(String.valueOf(expectedLimit));
        assertThat(headers.getFirst(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER)).isEqualTo(String.valueOf(remaining));
    }

    private void assertLimited(ResponseEntity<ErrorResponse> response, String path) {
        assertError(response, HttpStatus.TOO_MANY_REQUESTS, path);
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER)).isEqualTo("0");
    }
}
