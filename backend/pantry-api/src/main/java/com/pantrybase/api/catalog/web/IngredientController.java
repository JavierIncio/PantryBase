package com.pantrybase.api.catalog.web;

import com.pantrybase.api.catalog.dto.IngredientDetailResponse;
import com.pantrybase.api.catalog.dto.IngredientSearchResponse;
import com.pantrybase.api.catalog.service.IngredientCatalogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the ingredient catalog backed by the USDA FDC provider.
 */
@RestController
@RequestMapping("/api/catalog/ingredients")
public class IngredientController {

    private final IngredientCatalogService ingredientService;

    public IngredientController(IngredientCatalogService ingredientService) {
        this.ingredientService = ingredientService;
    }

    /** Searches the external catalog; rejects blank queries with HTTP 400. */
    @GetMapping
    public List<IngredientSearchResponse> search(@RequestParam(defaultValue = "") String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return ingredientService.search(query.trim());
    }

    /** Returns the ingredient detail, materializing it in the local catalog. */
    @GetMapping("/{fdcId}")
    public IngredientDetailResponse detail(@PathVariable long fdcId) {
        return ingredientService.getById(fdcId);
    }
}