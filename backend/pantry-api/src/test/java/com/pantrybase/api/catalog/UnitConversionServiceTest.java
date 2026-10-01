package com.pantrybase.api.catalog;

import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.IngredientDensity;
import com.pantrybase.api.catalog.domain.IngredientMeasure;
import com.pantrybase.api.catalog.domain.MeasureConversion;
import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.domain.Unit;
import com.pantrybase.api.catalog.domain.UnitCategory;
import com.pantrybase.api.catalog.exception.IngredientDensityNotFoundException;
import com.pantrybase.api.catalog.exception.IngredientNotFoundException;
import com.pantrybase.api.catalog.exception.UnknownUnitException;
import com.pantrybase.api.catalog.exception.UnsupportedConversionException;
import com.pantrybase.api.catalog.repository.IngredientDensityRepository;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UnitConversionServiceTest {
    @Mock UnitRepository unitRepo;
    @Mock MeasureConversionRepository conversionRepo;
    @Mock IngredientDensityRepository densityRepo;
    @Mock IngredientMeasureRepository measureRepo;
    @Mock IngredientRepository ingredientRepo;

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
                new BigDecimal("2"), "CUP", "TBSP", null, null);

        assertThat(q.unitCode()).isEqualTo("TBSP");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("31.999892"));
    }

    @Test
    void convert_sameCategory_toCanonical() {
        stubUnit("L", UnitCategory.VOLUME, false);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubFactor("L", "ML", "1000");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("4"), "L", "ML", null, null);

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
                new BigDecimal("2"), "CUP", "GRAM", null, "FLOUR");

        assertThat(q.unitCode()).isEqualTo("GRAM");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("250.78328"));
    }

    @Test
    void convert_weight_to_volume(){
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubDensity("FLOUR", "0.53");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("250.78328"), "GRAM", "ML", null, "FLOUR");

        assertThat(q.unitCode()).isEqualTo("ML");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("473.176"));
    }

    @Test
    void convert_unknownDensity() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubFactor("CUP", "ML", "236.588");

        assertThatThrownBy(() -> unitConversionService.convert(
                new BigDecimal("1"), "CUP", "GRAM", null, "BUTTER"))
                .isInstanceOf(IngredientDensityNotFoundException.class);
    }

    @Test
    void convert_unknownUnit_from() {
        assertThatThrownBy(() -> unitConversionService.convert(
                new BigDecimal("1"), "XXX", "GRAM", null, null))
                .isInstanceOf(UnknownUnitException.class);
    }

    @Test
    void convert_unsupported() {
        stubUnit("UNIT", UnitCategory.COUNT, true);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);

        assertThatThrownBy(() -> unitConversionService.convert(
                new BigDecimal("1"), "UNIT", "GRAM", null, null))
                .isInstanceOf(UnsupportedConversionException.class);
    }

    @Test
    void convert_amountZero() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubFactor("CUP", "ML", "236.588");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("0"), "CUP", "ML", null, null);

        assertThat(q.unitCode()).isEqualTo("ML");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("0"));
    }

    @Test
    void convert_negativeAmount_isNotValidatedByTheService() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("ML", UnitCategory.VOLUME, true);
        stubFactor("CUP", "ML", "236.588");

        var q = unitConversionService.convert(
                new BigDecimal("-1"), "CUP", "ML", null, null);

        assertThat(q.amount()).isEqualByComparingTo("-236.588");
    }

    @Test
    void convert_volumeToWeight_usesIngredientMeasure() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubIngredient(1L, "FLOUR");
        stubFactor("CUP", "ML", "236.588");
        stubMeasure(1L, "CUP", "244.000000");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("2"), "CUP", "GRAM", 1L, "FLOUR");

        assertThat(q.unitCode()).isEqualTo("GRAM");
        assertThat(q.amount()).isCloseTo(new BigDecimal("488"),
                within(new BigDecimal("0.001")));
    }

    @Test
    void convert_weightToVolume_usesIngredientMeasure() {
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubIngredient(1L, "FLOUR");
        stubFactor("CUP", "ML", "236.588");
        stubMeasure(1L, "CUP", "244.000000");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("488"), "GRAM", "CUP", 1L, "FLOUR");

        assertThat(q.unitCode()).isEqualTo("CUP");
        assertThat(q.amount()).isCloseTo(new BigDecimal("2"),
                within(new BigDecimal("0.00001")));
    }

    @Test
    void convert_volumeToWeight_usesAnotherMeasureWhenTheUnitWasNotCaptured() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubUnit("TBSP", UnitCategory.VOLUME, false);
        stubIngredient(1L, "FLOUR");
        stubFactor("CUP", "ML", "236.588");
        stubFactor("TBSP", "ML", "14.7868");
        when(measureRepo.findByIngredientIdAndUnitCode(1L, "CUP")).thenReturn(Optional.empty());
        when(measureRepo.findByIngredientId(1L)).thenReturn(List.of(measure("TBSP", "15.2")));

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("2"), "CUP", "GRAM", 1L, null);

        // One tablespoon is 15.2 g, so two cups (32 tablespoons) weigh about 486.4 g.
        // The residual comes from the seed factors: a cup is 236.588 ml while sixteen
        // tablespoons are 236.5888 ml, so deriving one from the other cannot be exact.
        assertThat(q.unitCode()).isEqualTo("GRAM");
        assertThat(q.amount()).isCloseTo(new BigDecimal("486.4"), within(new BigDecimal("0.01")));
    }

    @Test
    void convert_prefersTheLargestCapturedMeasure() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubUnit("TBSP", UnitCategory.VOLUME, false);
        stubUnit("PINT", UnitCategory.VOLUME, false);
        stubUnit("TSP", UnitCategory.VOLUME, false);
        stubIngredient(1L, "FLOUR");
        stubFactor("CUP", "ML", "236.588");
        stubFactor("TBSP", "ML", "14.7868");
        stubFactor("PINT", "ML", "473.176");
        stubFactor("TSP", "ML", "4.92892");
        when(measureRepo.findByIngredientIdAndUnitCode(1L, "CUP")).thenReturn(Optional.empty());
        when(measureRepo.findByIngredientId(1L)).thenReturn(List.of(
                measure("TBSP", "15.2"), measure("PINT", "500.0"), measure("TSP", "4.7")));

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("1"), "CUP", "GRAM", 1L, null);

        // 500 g per pint is the largest captured volume, so it defines the density.
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("250"));
    }

    @Test
    void convert_ingredientWithoutAnyMeasure_fallsBackToItsCategory() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        stubFactor("CUP", "ML", "236.588");
        stubIngredient(1L, "FLOUR");
        when(measureRepo.findByIngredientIdAndUnitCode(1L, "CUP")).thenReturn(Optional.empty());
        stubDensity("FLOUR", "0.53");

        QuantityInfo q = unitConversionService.convert(
                new BigDecimal("2"), "CUP", "GRAM", 1L, null);

        assertThat(q.unitCode()).isEqualTo("GRAM");
        assertThat(q.amount()).isEqualByComparingTo(new BigDecimal("250.78328"));
    }

    @Test
    void convert_unknownIngredientId_throwsNotFound() {
        stubUnit("CUP", UnitCategory.VOLUME, false);
        stubUnit("GRAM", UnitCategory.WEIGHT, true);
        when(ingredientRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> unitConversionService.convert(
                new BigDecimal("2"), "CUP", "GRAM", 99L, "FLOUR"))
                .isInstanceOf(IngredientNotFoundException.class);
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

    private void stubMeasure(Long ingredientId, String unitCode, String gramPerUnit) {
        when(measureRepo.findByIngredientIdAndUnitCode(ingredientId, unitCode))
                .thenReturn(Optional.of(measure(unitCode, gramPerUnit)));
    }

    private IngredientMeasure measure(String unitCode, String gramPerUnit) {
        IngredientMeasure m = new IngredientMeasure();
        m.setUnitCode(unitCode);
        m.setGramPerUnit(new BigDecimal(gramPerUnit));
        return m;
    }

    private void stubIngredient(Long id, String category) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(id);
        ingredient.setCategory(category);
        when(ingredientRepo.findById(id)).thenReturn(Optional.of(ingredient));
    }
}
