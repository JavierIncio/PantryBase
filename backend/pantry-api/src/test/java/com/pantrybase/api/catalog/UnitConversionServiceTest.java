package com.pantrybase.api.catalog;

import com.pantrybase.api.catalog.domain.IngredientDensity;
import com.pantrybase.api.catalog.domain.MeasureConversion;
import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.domain.Unit;
import com.pantrybase.api.catalog.domain.UnitCategory;
import com.pantrybase.api.catalog.exception.IngredientDensityNotFoundException;
import com.pantrybase.api.catalog.exception.UnknownUnitException;
import com.pantrybase.api.catalog.exception.UnsupportedConversionException;
import com.pantrybase.api.catalog.repository.IngredientDensityRepository;
import com.pantrybase.api.catalog.repository.MeasureConversionRepository;
import com.pantrybase.api.catalog.repository.UnitRepository;
import com.pantrybase.api.catalog.service.UnitConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UnitConversionServiceTest {
    @Mock UnitRepository unitRepo;
    @Mock MeasureConversionRepository conversionRepo;
    @Mock IngredientDensityRepository densityRepo;

    @InjectMocks
    UnitConversionService unitConversionService;

    @Test
    void toCanonical_nonCanonical() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubFactor("CUP", "ML", "236.588");

        QuantityInfo q = unitConversionService.toCanonical(
                new BigDecimal("2"), "CUP");

        assertThat(q.unitCode()).isEqualTo("ML");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("473.176"));
    }

    @Test
    void toCanonical_canonical_passthrough() {
        stubUnit("GRAM", UnitCategory.WEIGHT, true);

        QuantityInfo q = unitConversionService.toCanonical(
                new BigDecimal("2"), "GRAM");

        assertThat(q.unitCode()).isEqualTo("GRAM");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    void toCanonical_unknownUnit() {
        assertThatThrownBy(() ->
                unitConversionService.toCanonical(
                        new BigDecimal("2"), "XXX"))
                .isInstanceOf(UnknownUnitException.class);
    }

    @Test
    void convert_sameCategory_toNonCanonical() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("TBSP", UnitCategory.VOLUME, false);
        stubFactor("CUP", "ML", "236.588");
        stubFactor("TBSP", "ML", "14.7868");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("2"), "CUP", "TBSP", null);

        assertThat(q.unitCode()).isEqualTo("TBSP");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("31.999892"));
    }

    @Test
    void convert_sameCategory_toCanonical() {
        stubUnit("L", UnitCategory.VOLUME, false);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubFactor("L", "ML", "1000");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("4"), "L", "ML", null);

        assertThat(q.unitCode()).isEqualTo("ML");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    void convert_volume_to_weight() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubFactor("CUP", "ML", "236.588");
        stubDensity("FLOUR", "0.53");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("2"), "CUP", "GRAM", "FLOUR");

        assertThat(q.unitCode()).isEqualTo("GRAM");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("250.78328"));
    }

    @Test
    void convert_weight_to_volume(){
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubDensity("FLOUR", "0.53");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("250.78328"), "GRAM", "ML", "FLOUR");

        assertThat(q.unitCode()).isEqualTo("ML");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("473.176"));
    }

    @Test
    void convert_unknownDensity() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);

        assertThatThrownBy(() -> unitConversionService.convert(
                new BigDecimal("1"), "CUP", "GRAM", "BUTTER"))
                .isInstanceOf(IngredientDensityNotFoundException.class);
    }

    @Test
    void convert_unknownUnit_from() {
        assertThatThrownBy(() -> unitConversionService.convert(
                new BigDecimal("1"), "XXX", "GRAM", null))
                .isInstanceOf(UnknownUnitException.class);
    }

    @Test
    void convert_unsupported() {
        stubUnit("UNIT", UnitCategory.COUNT, true);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);

        assertThatThrownBy(() -> unitConversionService.convert(
                new BigDecimal("1"), "UNIT", "GRAM", null))
                .isInstanceOf(UnsupportedConversionException.class);
    }

    @Test
    void convert_amountZero() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubFactor("CUP", "ML", "236.588");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("0"), "CUP", "ML", null);

        assertThat(q.unitCode()).isEqualTo("ML");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("0"));
    }

    @Test
    void convert_negativeAmount_isNotValidatedByTheService() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubFactor("CUP", "ML", "236.588");

        var q = unitConversionService.convert(
                new BigDecimal("-1"), "CUP", "ML", null);

        assertThat(q.amount()).isEqualByComparingTo("-236.588");
    }

    private void stubUnit(String code, UnitCategory cat, boolean canonical) {
        Unit u = new Unit();
        u.setCode(code);
        u.setCategory(cat);
        u.setCanonical(canonical);
        when(unitRepo.findById(code)).thenReturn(Optional.of(u));
    }

    private void stubFactor(String from, String to, String factorStr) {
        MeasureConversion mc = new MeasureConversion();
        mc.setUnitCode(from);
        Unit canonical = new Unit();
        canonical.setCode(to);
        mc.setCanonicalUnit(canonical);
        mc.setConversionFactor(new BigDecimal(factorStr));
        when(conversionRepo.findByUnitCode(from)).thenReturn(Optional.of(mc));
    }

    private void stubDensity(String category, String gPerMl) {
        IngredientDensity d = new IngredientDensity();
        d.setIngredientCategory(category);
        d.setDensityGPerMl(new BigDecimal(gPerMl));
        when(densityRepo.findById(category)).thenReturn(Optional.of(d));
    }
}
