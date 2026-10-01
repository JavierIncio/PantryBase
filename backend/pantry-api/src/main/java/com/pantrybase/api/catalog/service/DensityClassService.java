package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.DensityClass;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.dto.AssignDensityClassRequest;
import com.pantrybase.api.catalog.dto.DensityClassResponse;
import com.pantrybase.api.catalog.dto.IngredientDensityClassResponse;
import com.pantrybase.api.catalog.exception.DensityClassNotFoundException;
import com.pantrybase.api.catalog.exception.IngredientNotFoundException;
import com.pantrybase.api.catalog.repository.DensityClassRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Curation of the ingredients' density classes.
 *
 * <p>Kept apart from {@link IngredientCatalogService} because curation mutates our own
 * data while that one orchestrates the external provider: the distinction matters
 * because the class is the one field of an ingredient that materialization must never
 * overwrite.</p>
 */
@Service
@Transactional(readOnly = true)
public class DensityClassService {

    private final DensityClassRepository densityClassRepo;
    private final IngredientRepository ingredientRepo;

    public DensityClassService(DensityClassRepository densityClassRepo,
                               IngredientRepository ingredientRepo) {
        this.densityClassRepo = densityClassRepo;
        this.ingredientRepo = ingredientRepo;
    }

    /** Lists the available classes ordered by code, so the result is deterministic. */
    public List<DensityClassResponse> listClasses() {
        return densityClassRepo.findAll(Sort.by("code")).stream()
                .map(c -> new DensityClassResponse(c.getCode(), c.getDensityGPerMl(), c.getSource()))
                .toList();
    }

    /**
     * Assigns a class to an ingredient, replacing any previous assignment.
     *
     * <p>Only the class is written, so the assignment survives later provider lookups:
     * materialization inserts the ingredient but never updates it.</p>
     *
     * <p>The class must already exist, which is what keeps this endpoint from becoming
     * a way to introduce a density nobody has justified.</p>
     *
     * @param ingredientId internal ingredient id, not the provider's fdcId
     * @throws IngredientNotFoundException   if no such ingredient has been materialized
     * @throws DensityClassNotFoundException if the class code is not registered
     */
    @Transactional
    public IngredientDensityClassResponse assign(long ingredientId, AssignDensityClassRequest request) {
        DensityClass densityClass = densityClassRepo.findById(request.densityClass())
                .orElseThrow(() -> new DensityClassNotFoundException(request.densityClass()));

        Ingredient ingredient = ingredientRepo.findById(ingredientId)
                .orElseThrow(() -> new IngredientNotFoundException(ingredientId));
        ingredient.setDensityClass(densityClass.getCode());

        return new IngredientDensityClassResponse(
                ingredient.getId(), ingredient.getFdcId(), ingredient.getName(),
                ingredient.getDensityClass());
    }
}
