package com.pantrybase.api.user;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

public class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void me_withBearerToken_shouldReturn200() {
        Session session = registerSession();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.tokens().accessToken());

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

        assertError(response, HttpStatus.UNAUTHORIZED, ME);
    }

    @Test
    void me_withInvalidToken_shouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.jwt.token");

        ResponseEntity<ErrorResponse> response =
                rest.exchange(ME, HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(response, HttpStatus.UNAUTHORIZED, ME);
    }

}
