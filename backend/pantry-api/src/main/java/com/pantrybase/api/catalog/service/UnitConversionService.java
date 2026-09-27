package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.IngredientDensity;
import com.pantrybase.api.catalog.domain.MeasureConversion;
import com.pantrybase.api.catalog.domain.Unit;
import com.pantrybase.api.catalog.domain.UnitCategory;
import com.pantrybase.api.catalog.domain.UnitConverter;
import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.exception.IngredientDensityNotFoundException;
import com.pantrybase.api.catalog.exception.MeasureConversionNotFoundException;
import com.pantrybase.api.catalog.exception.UnknownUnitException;
import com.pantrybase.api.catalog.exception.UnsupportedConversionException;
import com.pantrybase.api.catalog.repository.IngredientDensityRepository;
import com.pantrybase.api.catalog.repository.MeasureConversionRepository;
import com.pantrybase.api.catalog.repository.UnitRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class UnitConversionService implements UnitConverter {

    private final UnitRepository unitRepo;
    private final MeasureConversionRepository measureConversionRepo;
    private final IngredientDensityRepository ingredientDensityRepo;

    public UnitConversionService(UnitRepository unitRepo,
                                 MeasureConversionRepository measureConversionRepo,
                                 IngredientDensityRepository ingredientDensityRepo) {
        this.unitRepo = unitRepo;
        this.measureConversionRepo = measureConversionRepo;
        this.ingredientDensityRepo = ingredientDensityRepo;
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
     *   <li>If they belong to different categories, it uses the ingredient density to convert between weight and volume.</li>
     *   <ul>
     *        <li>If converting from weight to volume, it divides the weight by the density.</li>
     *        <li>If converting from volume to weight, it multiplies the volume by the density.</li>
     *   </ul>
     * </ul>
     *
     * @param amount             The amount to convert.
     * @param fromCode           The unit code of the original amount.
     * @param toCode             The unit code to convert to.
     * @param ingredientCategory The category of the ingredient for density-based conversions.
     * @return A QuantityInfo object containing the converted amount and target unit code.
     * @throws UnknownUnitException if either unit code is not found in the repository.
     * @throws IngredientDensityNotFoundException if the ingredient category is not found in the repository.
     * @throws UnsupportedConversionException      if conversion between the two unit categories is not supported.
     */
    @Override
    public QuantityInfo convert(BigDecimal amount, String fromCode, String toCode, String ingredientCategory) {

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

        IngredientDensity density = ingredientDensityRepo.findById(ingredientCategory)
                .orElseThrow(() -> new IngredientDensityNotFoundException(ingredientCategory));

        BigDecimal canonicalAmount = toCanonical(amount, fromCode).amount();

        BigDecimal converted = weightToVolume
                ? canonicalAmount.divide(density.getDensityGPerMl(), 6, RoundingMode.HALF_UP)
                : canonicalAmount.multiply(density.getDensityGPerMl());

        return scaleTo(converted, toCode);
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
