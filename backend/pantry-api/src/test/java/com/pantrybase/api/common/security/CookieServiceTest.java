package com.pantrybase.api.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookieServiceTest {

    @Mock
    private JwtService jwtService;

    @Test
    void createMarksCookieSecureWhenFlagIsTrue() {
        when(jwtService.getRefreshTtl()).thenReturn(Duration.ofDays(7));

        ResponseCookie cookie = new CookieService(jwtService, true).create("token");

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void createLeavesCookieInsecureWhenFlagIsFalse() {
        when(jwtService.getRefreshTtl()).thenReturn(Duration.ofDays(7));

        ResponseCookie cookie = new CookieService(jwtService, false).create("token");

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void clearKeepsTheSameSecureFlagAndExpiresImmediately() {
        ResponseCookie cookie = new CookieService(jwtService, true).clear();

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getValue()).isEmpty();
    }
}
