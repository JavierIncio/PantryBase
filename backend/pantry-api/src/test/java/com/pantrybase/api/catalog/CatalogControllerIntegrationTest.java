package com.pantrybase.api.catalog;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.MeasureConversion;
import com.pantrybase.api.catalog.dto.ConvertUnitsRequest;
import com.pantrybase.api.catalog.dto.UnitConversionResponse;
import com.pantrybase.api.catalog.dto.UnitResponse;
import com.pantrybase.api.catalog.repository.MeasureConversionRepository;
import com.pantrybase.api.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalogControllerIntegrationTest extends AbstractIntegrationTest {
    public static final String UNITS = "/api/units";
    public static final String UNITS_CONVERT = "/api/units/convert";

    @Test
    void list_shouldReturnSortedList() {
        HttpHeaders headers = authenticate();

        ResponseEntity<UnitResponse[]> response =
                rest.exchange(UNITS, HttpMethod.GET, new HttpEntity<>(headers), UnitResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(12);
        assertThat(response.getBody()).extracting(UnitResponse::code).isSorted();
    }

    @Test
    void convert_validConversion_shouldReturnConvertedValue() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                BigDecimal.ONE, "PINT", "GRAM", null, "FLOUR");

        ResponseEntity<UnitConversionResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), UnitConversionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().amount()).isEqualByComparingTo(new BigDecimal("250.78328"));
        assertThat(response.getBody().unitCode()).isEqualTo("GRAM");
    }

    @Test
    void convert_zeroAmount_shouldReturn200() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                BigDecimal.ZERO, "CUP", "ML", null, null);

        ResponseEntity<UnitConversionResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), UnitConversionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getBody().unitCode()).isEqualTo("ML");
    }

    @Test
    void convert_negativeAmount_shouldReturn400() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                new BigDecimal("-2"), "CUP", "ML", null, null);

        ResponseEntity<ErrorResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void convert_blankUnitCode_shouldReturn400() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                new BigDecimal("2.5"), "", "ML", null, null);

        ResponseEntity<ErrorResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void convert_unknownUnitFromCode_shouldReturn400() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                new BigDecimal("2"), "XXX", "ML", null, null);

        ResponseEntity<ErrorResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void convert_densityClassWithoutDensity_shouldReturn400() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                new BigDecimal("2"), "CUP", "GRAM", null, "UNKNOWN_CLASS");

        ResponseEntity<ErrorResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void convert_fromCountToWeight_shouldReturn400() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                new BigDecimal("2"), "UNIT", "GRAM", null, null);

        ResponseEntity<ErrorResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void convert_fromVolumeToWeightWithoutCategory_shouldReturn400() {
        HttpHeaders headers = authenticate();

        ConvertUnitsRequest body = new ConvertUnitsRequest(
                new BigDecimal("2"), "CUP", "GRAM", null, null);

        ResponseEntity<ErrorResponse> response =
                rest.exchange(UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void check_nonCanonicalUnit_hasFactor() {
        HttpHeaders headers = authenticate();

        ResponseEntity<UnitResponse[]> response =
                rest.exchange(UNITS, HttpMethod.GET, new HttpEntity<>(headers), UnitResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        assertThat(response.getBody())
                .filteredOn(unit -> !unit.canonical())
                .allSatisfy(unit -> {
                    assertThat(measureConversionRepository.findByUnitCode(unit.code()))
                            .as("Conversion factor for non-canonical unit %s", unit.code())
                            .isPresent();
                });
    }

    @Test
    void unauthorizedAccess_shouldReturn401() {
        assertUnauthorized(UNITS, HttpMethod.GET);
        assertUnauthorized(UNITS_CONVERT, HttpMethod.POST);
    }
}
