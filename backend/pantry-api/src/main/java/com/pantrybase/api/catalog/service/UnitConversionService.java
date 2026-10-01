package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.IngredientDensity;
import com.pantrybase.api.catalog.domain.IngredientMeasure;
import com.pantrybase.api.catalog.domain.MeasureConversion;
import com.pantrybase.api.catalog.domain.Unit;
import com.pantrybase.api.catalog.domain.UnitCategory;
import com.pantrybase.api.catalog.domain.UnitConverter;
import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.exception.IngredientCategoryRequiredException;
import com.pantrybase.api.catalog.exception.IngredientDensityNotFoundException;
import com.pantrybase.api.catalog.exception.IngredientNotFoundException;
import com.pantrybase.api.catalog.exception.MeasureConversionNotFoundException;
import com.pantrybase.api.catalog.exception.UnknownUnitException;
import com.pantrybase.api.catalog.exception.UnsupportedConversionException;
import com.pantrybase.api.catalog.repository.IngredientDensityRepository;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.catalog.repository.MeasureConversionRepository;
import com.pantrybase.api.catalog.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Optional;

/**
 * Service class for handling unit conversions.
 *
 * <p>Transactional read-only operations to omit dirty-checking and improve performance.</p>
 */
@Service
@Transactional(readOnly = true)
public class UnitConversionService implements UnitConverter {

    private static final int DENSITY_SCALE = 6;

    private final UnitRepository unitRepo;
    private final MeasureConversionRepository measureConversionRepo;
    private final IngredientDensityRepository ingredientDensityRepo;
    private final IngredientMeasureRepository ingredientMeasureRepo;
    private final IngredientRepository ingredientRepo;

    public UnitConversionService(UnitRepository unitRepo,
                                 MeasureConversionRepository measureConversionRepo,
                                 IngredientDensityRepository ingredientDensityRepo,
                                 IngredientMeasureRepository ingredientMeasureRepo,
                                 IngredientRepository ingredientRepo) {
        this.unitRepo = unitRepo;
        this.measureConversionRepo = measureConversionRepo;
        this.ingredientDensityRepo = ingredientDensityRepo;
        this.ingredientMeasureRepo = ingredientMeasureRepo;
        this.ingredientRepo = ingredientRepo;
    }

    /**
     * Converts the given amount and unit code to its canonical form.
     *
     * <p>If the unit is already canonical, it returns the same amount and unit code.</p>
     *
     * @param amount   The amount to convert.
     * @param unitCode The unit code of the amount.
     * @return A QuantityInfo object containing the converted amount and canonical unit code.
     * @throws UnknownUnitException if the unit code is not found in the repository.
     */
    @Override
    public QuantityInfo toCanonical(BigDecimal amount, String unitCode) {
        Unit unit = unitRepo.findById(unitCode)
                .orElseThrow(() -> new UnknownUnitException(unitCode));

        if (unit.isCanonical()) return new QuantityInfo(amount, unitCode);

        MeasureConversion conversion = measureConversionRepo.findByUnitCode(unitCode)
                .orElseThrow(() -> new MeasureConversionNotFoundException(unitCode));

        String canonicalUnitCode = conversion.getCanonicalUnit().getCode();
        BigDecimal convertedAmount = conversion.getConversionFactor().multiply(amount);

        return new QuantityInfo(convertedAmount, canonicalUnitCode);
    }

    /**
     * Converts the given amount from one unit to another, considering the ingredient category for density-based conversions.
     *
     * <ul>
     *   <li>If both units belong to the same category, it performs a direct conversion.</li>
     *   <li>If they belong to different categories, it resolves a density in grams per milliliter.</li>
     *   <ul>
     *        <li>If the ingredient has a captured measure for the volume unit involved
     *            (a household measure such as one cup), that measure wins; it is the
     *            ingredient's own weight rather than an average of the category.</li>
     *        <li>Otherwise any other captured measure of that ingredient defines the
     *            density, so a tablespoon still converts cups without leaving the
     *            ingredient's own data.</li>
     *        <li>Only when the ingredient has no measure at all does it fall back to the
     *            density of the category, the one of the given ingredient when present,
     *            else the one passed.</li>
     *        <li>If converting from weight to volume, it divides the weight by the density.</li>
     *        <li>If converting from volume to weight, it multiplies the volume by the density.</li>
     *   </ul>
     * </ul>
     *
     * @param amount             The amount to convert.
     * @param fromCode           The unit code of the original amount.
     * @param toCode             The unit code to convert to.
     * @param ingredientId       Optional ingredient whose own measures take precedence over the category.
     * @param ingredientCategory The category of the ingredient for density-based conversions.
     * @return A QuantityInfo object containing the converted amount and target unit code.
     * @throws UnknownUnitException if either unit code is not found in the repository.
     * @throws IngredientNotFoundException if the given ingredientId is not found in the repository.
     * @throws IngredientCategoryRequiredException if a density is needed and no ingredient nor category is given.
     * @throws IngredientDensityNotFoundException if the ingredient has no measure and the category has no density.
     * @throws UnsupportedConversionException      if conversion between the two unit categories is not supported.
     */
    @Override
    public QuantityInfo convert(BigDecimal amount, String fromCode, String toCode,
                                Long ingredientId, String ingredientCategory) {

        Unit unitFrom = unitRepo.findById(fromCode)
                .orElseThrow(() -> new UnknownUnitException(fromCode));

        Unit unitTo = unitRepo.findById(toCode)
                .orElseThrow(() -> new UnknownUnitException(toCode));

        if (unitFrom.getCategory() == unitTo.getCategory()) {
            QuantityInfo canonical = toCanonical(amount, fromCode);
            return scaleTo(canonical.amount(), toCode);
        }

        boolean weightToVolume = unitFrom.getCategory() == UnitCategory.WEIGHT
                && unitTo.getCategory() == UnitCategory.VOLUME;
        boolean volumeToWeight = unitFrom.getCategory() == UnitCategory.VOLUME
                && unitTo.getCategory() == UnitCategory.WEIGHT;

        if (!weightToVolume && !volumeToWeight)
            throw new UnsupportedConversionException(
                    unitFrom.getCategory().name(), unitTo.getCategory().name());

        String volumeCode = weightToVolume ? toCode : fromCode;
        Ingredient ingredient = resolveIngredient(ingredientId);

        // The source amount is always canonicalized through fromCode: it is the real input.
        BigDecimal canonicalAmount = toCanonical(amount, fromCode).amount();

        BigDecimal converted = resolveSourceMeasure(ingredientId, volumeCode)
                .map(m -> convertByMeasure(canonicalAmount, m, weightToVolume))
                .orElseGet(() -> convertByDensity(canonicalAmount, ingredient,
                        ingredientCategory, fromCode, toCode, weightToVolume));

        return scaleTo(converted, toCode);
    }

    /**
     * Picks the ingredient measure that will define the density of this conversion.
     *
     * <p>Prefers the measure of the exact volume unit involved, because the provider may
     * have weighed a different amount for it. When that unit was not captured, any other
     * measure of the same ingredient already defines its density, which spares us from
     * falling back to a category average; the largest volume wins because a gram weight
     * measured on a cup is more precise than the same weight on a teaspoon.</p>
     */
    private Optional<IngredientMeasure> resolveSourceMeasure(Long ingredientId, String volumeCode) {
        if (ingredientId == null) return Optional.empty();

        Optional<IngredientMeasure> exact =
                ingredientMeasureRepo.findByIngredientIdAndUnitCode(ingredientId, volumeCode);
        if (exact.isPresent()) return exact;

        return ingredientMeasureRepo.findByIngredientId(ingredientId).stream()
                .max(Comparator.comparing(m -> millilitersPerUnit(m.getUnitCode())));
    }

    /**
     * Converts through one of the ingredient's own measures, as the ratio between the
     * volume of the measure unit and its weight, instead of flattening it into a rounded
     * density.
     *
     * <p>Keeps the conversion exact: two cups of a 244 g-per-cup ingredient are exactly
     * 488 g, whereas dividing by a density rounded to six decimals would drift. The
     * measure does not need to be the requested unit, since a single measure is enough
     * to know how the ingredient's volume relates to its weight.</p>
     */
    private BigDecimal convertByMeasure(BigDecimal canonicalAmount, IngredientMeasure measure,
                                        boolean weightToVolume) {
        BigDecimal mlPerUnit = millilitersPerUnit(measure.getUnitCode());
        BigDecimal gramPerUnit = measure.getGramPerUnit();

        return weightToVolume
                ? canonicalAmount.divide(gramPerUnit, DENSITY_SCALE, RoundingMode.HALF_UP)
                        .multiply(mlPerUnit)
                : canonicalAmount.divide(mlPerUnit, DENSITY_SCALE, RoundingMode.HALF_UP)
                        .multiply(gramPerUnit);
    }

    /** Converts through the density of the category, the fallback when the ingredient has no measure. */
    private BigDecimal convertByDensity(BigDecimal canonicalAmount,
                                        Ingredient ingredient, String ingredientCategory,
                                        String fromCode, String toCode, boolean weightToVolume) {
        BigDecimal density = resolveDensity(ingredient, ingredientCategory, fromCode, toCode);

        return weightToVolume
                ? canonicalAmount.divide(density, DENSITY_SCALE, RoundingMode.HALF_UP)
                : canonicalAmount.multiply(density);
    }

    /**
     * Resolves the ingredient when one is given. It is loaded even when its measure alone
     * would be enough, because a non-existing id is a client error that must not be
     * silently downgraded to a category-based conversion.
     */
    private Ingredient resolveIngredient(Long ingredientId) {
        if (ingredientId == null) return null;

        return ingredientRepo.findById(ingredientId)
                .orElseThrow(() -> new IngredientNotFoundException(ingredientId));
    }

    /**
     * Resolves the density in grams per milliliter of the category, preferring the
     * category of the given ingredient over the one passed as a fallback.
     */
    private BigDecimal resolveDensity(Ingredient ingredient, String ingredientCategory,
                                      String fromCode, String toCode) {
        String category = ingredientCategory;
        if ((category == null || category.isBlank()) && ingredient != null) {
            category = ingredient.getCategory();
        }
        if (category == null || category.isBlank())
            throw new IngredientCategoryRequiredException(fromCode, toCode);

        final String resolvedCategory = category;
        return ingredientDensityRepo.findById(resolvedCategory)
                .orElseThrow(() -> new IngredientDensityNotFoundException(resolvedCategory))
                .getDensityGPerMl();
    }

    private BigDecimal millilitersPerUnit(String volumeCode) {
        if (unitRepo.findById(volumeCode)
                .orElseThrow(() -> new UnknownUnitException(volumeCode))
                .isCanonical()) {
            return BigDecimal.ONE;
        }
        return measureConversionRepo.findByUnitCode(volumeCode)
                .orElseThrow(() -> new MeasureConversionNotFoundException(volumeCode))
                .getConversionFactor();
    }

    private QuantityInfo scaleTo(BigDecimal amount, String toCode) {
        Unit to = unitRepo.findById(toCode)
                .orElseThrow(() -> new UnknownUnitException(toCode));

        if (to.isCanonical()) return new QuantityInfo(amount, toCode);

        MeasureConversion conversion = measureConversionRepo.findByUnitCode(toCode)
                .orElseThrow(() -> new MeasureConversionNotFoundException(toCode));

        BigDecimal convertedAmount = amount.divide(
                conversion.getConversionFactor(), 6, RoundingMode.HALF_UP);

        return new QuantityInfo(convertedAmount, toCode);
    }
}
