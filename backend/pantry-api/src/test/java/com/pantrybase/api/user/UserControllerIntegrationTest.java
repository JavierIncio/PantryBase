package com.pantrybase.api.user;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.user.domain.Diet;
import com.pantrybase.api.user.domain.FilterMode;
import com.pantrybase.api.user.dto.AllergenResponse;
import com.pantrybase.api.user.dto.AllergyExclusionsResponse;
import com.pantrybase.api.user.dto.ReplaceAllergyExclusionsRequest;
import com.pantrybase.api.user.dto.UserPreferencesResponse;
import com.pantrybase.api.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UserControllerIntegrationTest extends AbstractIntegrationTest {

    private final static String PREFERENCES = "/api/users/preferences";
    private final static String EXCLUSIONS = "/api/users/allergy-exclusions";
    private final static String ALLERGENS = "/api/users/allergens";

    @Test
    void me_withBearerToken_shouldReturn200() {
        HttpHeaders headers = authenticate();

        ResponseEntity<UserResponse> response =
                rest.exchange(ME, HttpMethod.GET, new HttpEntity<>(headers), UserResponse.class);

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

    @Test
    void getPreferences_withoutRow_shouldReturnDefaultsAndPersistNothing() {
        HttpHeaders headers = authenticate();

        ResponseEntity<UserPreferencesResponse> response = rest.exchange(
                PREFERENCES, HttpMethod.GET, new HttpEntity<>(headers), UserPreferencesResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        assertThat(response.getBody())
                .extracting(UserPreferencesResponse::filterMode,
                        UserPreferencesResponse::coverageThreshold,
                        UserPreferencesResponse::diet)
                .containsExactly(FilterMode.LAX, (short) 80, Diet.BALANCED);
        assertThat(preferencesRepository.count()).isZero();
    }

    @Test
    void savePreferences_shouldPersistAndReturn200() {
        HttpHeaders headers = authenticate();

        UserPreferencesResponse preferencesToSave =
                new UserPreferencesResponse(FilterMode.STRICT, (short) 100, Diet.LOW_CARB);

        ResponseEntity<UserPreferencesResponse> response = rest.exchange(
                PREFERENCES, HttpMethod.PUT, new HttpEntity<>(preferencesToSave, headers), UserPreferencesResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        assertThat(response.getBody())
                .extracting(UserPreferencesResponse::filterMode,
                        UserPreferencesResponse::coverageThreshold,
                        UserPreferencesResponse::diet)
                .containsExactly(FilterMode.STRICT, (short) 100, Diet.LOW_CARB);

        assertThat(preferencesRepository.count()).isEqualTo(1);
    }

    @Test
    void savePreferences_withInvalidCoverageThreshold_shouldReturn400() {
        HttpHeaders headers = authenticate();

        UserPreferencesResponse prefsOverThreshold =
                new UserPreferencesResponse(FilterMode.LAX, (short) 101, Diet.BALANCED);
        ResponseEntity<ErrorResponse> responseOverThreshold = rest.exchange(
                PREFERENCES, HttpMethod.PUT, new HttpEntity<>(prefsOverThreshold, headers), ErrorResponse.class);
        assertError(responseOverThreshold, HttpStatus.BAD_REQUEST, PREFERENCES);

        UserPreferencesResponse prefsUnderThreshold =
                new UserPreferencesResponse(FilterMode.LAX, (short) -1, Diet.BALANCED);
        ResponseEntity<ErrorResponse> responseUnderThreshold = rest.exchange(
                PREFERENCES, HttpMethod.PUT, new HttpEntity<>(prefsUnderThreshold, headers), ErrorResponse.class);
        assertError(responseUnderThreshold, HttpStatus.BAD_REQUEST, PREFERENCES);

        assertThat(preferencesRepository.count()).isZero();
    }

    @Test
    void getExclusions_withoutRow_shouldReturnEmptyList() {
        HttpHeaders headers = authenticate();

        ResponseEntity<AllergyExclusionsResponse> response = rest.exchange(
                EXCLUSIONS, HttpMethod.GET, new HttpEntity<>(headers), AllergyExclusionsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exclusions()).isEmpty();
    }

    @Test
    void replaceExclusions_shouldPersistAndReturn200() {
        HttpHeaders headers = authenticate();

        var body = new ReplaceAllergyExclusionsRequest(List.of("PEANUT", "GLUTEN"));

        ResponseEntity<AllergyExclusionsResponse> response = rest.exchange(
                EXCLUSIONS, HttpMethod.PUT, new HttpEntity<>(body, headers), AllergyExclusionsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exclusions())
                .extracting("code")
                .containsExactlyInAnyOrder("PEANUT", "GLUTEN");

        ResponseEntity<AllergyExclusionsResponse> after = rest.exchange(
                EXCLUSIONS, HttpMethod.GET, new HttpEntity<>(headers), AllergyExclusionsResponse.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody()).isNotNull();
        assertThat(after.getBody().exclusions())
                .extracting(AllergenResponse::code)
                .containsExactlyInAnyOrder("PEANUT", "GLUTEN");
    }

    @Test
    void replaceExclusions_withNewValues_shouldReplaceExistingExclusions() {
        HttpHeaders headers = authenticate();

        var body = new ReplaceAllergyExclusionsRequest(List.of("PEANUT"));

        ResponseEntity<AllergyExclusionsResponse> response = rest.exchange(
                EXCLUSIONS, HttpMethod.PUT, new HttpEntity<>(body, headers), AllergyExclusionsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exclusions())
                .extracting("code")
                .containsExactly("PEANUT");

        ResponseEntity<AllergyExclusionsResponse> after = rest.exchange(
                EXCLUSIONS, HttpMethod.GET, new HttpEntity<>(headers), AllergyExclusionsResponse.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody()).isNotNull();
        assertThat(after.getBody().exclusions())
                .extracting(AllergenResponse::code)
                .containsExactly("PEANUT");
    }

    @Test
    void replaceExclusions_withEmptyArray_shouldCleanExistingExclusions() {
        HttpHeaders headers = authenticate();

        var body = new ReplaceAllergyExclusionsRequest(List.of());

        ResponseEntity<AllergyExclusionsResponse> response = rest.exchange(
                EXCLUSIONS, HttpMethod.PUT, new HttpEntity<>(body, headers), AllergyExclusionsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exclusions()).isEmpty();

        ResponseEntity<AllergyExclusionsResponse> after = rest.exchange(
                EXCLUSIONS, HttpMethod.GET, new HttpEntity<>(headers), AllergyExclusionsResponse.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody()).isNotNull();
        assertThat(after.getBody().exclusions()).isEmpty();
    }

    @Test
    void replaceExclusions_withInvalidValues_shouldReturn400() {
        HttpHeaders headers = authenticate();

        var body = new ReplaceAllergyExclusionsRequest(List.of("NOPE"));

        ResponseEntity<ErrorResponse> response = rest.exchange(
                EXCLUSIONS, HttpMethod.PUT, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, EXCLUSIONS);
    }

    @Test
    void replaceExclusions_withRepeatedValues_shouldPersistOneAndReturn200() {
        HttpHeaders headers = authenticate();

        var body = new ReplaceAllergyExclusionsRequest(List.of("PEANUT", "PEANUT"));

        ResponseEntity<AllergyExclusionsResponse> response = rest.exchange(
                EXCLUSIONS, HttpMethod.PUT, new HttpEntity<>(body, headers), AllergyExclusionsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exclusions()).isNotEmpty();
        assertThat(response.getBody().exclusions())
                .extracting("code")
                .containsExactly("PEANUT");
        assertThat(response.getBody().exclusions()).hasSize(1);

        ResponseEntity<AllergyExclusionsResponse> after = rest.exchange(
                EXCLUSIONS, HttpMethod.GET, new HttpEntity<>(headers), AllergyExclusionsResponse.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody()).isNotNull();
        assertThat(after.getBody().exclusions())
                .extracting(AllergenResponse::code)
                .containsExactly("PEANUT");
        assertThat(after.getBody().exclusions()).hasSize(1);
    }

    @Test
    void getAllergens_shouldReturnTheSeededOntology() {
        HttpHeaders headers = authenticate();

        ResponseEntity<AllergenResponse[]> response = rest.exchange(
                ALLERGENS, HttpMethod.GET, new HttpEntity<>(headers), AllergenResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(14);
        assertThat(response.getBody()).extracting(AllergenResponse::name)
                .isSorted();
        assertThat(response.getBody()).extracting(AllergenResponse::code)
                .contains("PEANUT", "GLUTEN", "MILK", "CRUSTACEAN");
    }

    @Test
    void unauthorizedAccess_shouldReturn401() {
        assertUnauthorized(PREFERENCES, HttpMethod.GET);
        assertUnauthorized(PREFERENCES, HttpMethod.PUT);
        assertUnauthorized(EXCLUSIONS, HttpMethod.GET);
        assertUnauthorized(EXCLUSIONS, HttpMethod.PUT);
        assertUnauthorized(ALLERGENS, HttpMethod.GET);
    }

    private HttpHeaders authenticate() {
        Session session = registerSession();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.tokens().accessToken());

        return headers;
    }

    private void assertUnauthorized(String endpoint, HttpMethod method) {
        ResponseEntity<ErrorResponse> response = rest.exchange(
                endpoint, method, HttpEntity.EMPTY,
                ErrorResponse.class
        );

        assertError(response, HttpStatus.UNAUTHORIZED, endpoint);
    }
}
