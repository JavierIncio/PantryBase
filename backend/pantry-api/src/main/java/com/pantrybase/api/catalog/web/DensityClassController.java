package com.pantrybase.api.catalog.web;

import com.pantrybase.api.catalog.dto.AssignDensityClassRequest;
import com.pantrybase.api.catalog.dto.DensityClassResponse;
import com.pantrybase.api.catalog.dto.IngredientDensityClassResponse;
import com.pantrybase.api.catalog.service.DensityClassService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administration of the curated density classes.
 *
 * <p>Read access is open to any authenticated user because callers need to know which
 * classes exist to decide whether a conversion will work; the assignment is restricted
 * to administrators because it mutates data shared by every user, and an unverified
 * class would silently distort everyone's recipes.</p>
 */
@RestController
@RequestMapping("/api/catalog/density-classes")
public class DensityClassController {

    private final DensityClassService densityClassService;

    public DensityClassController(DensityClassService densityClassService) {
        this.densityClassService = densityClassService;
    }

    @GetMapping
    public List<DensityClassResponse> list() {
        return densityClassService.listClasses();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/ingredients/{ingredientId}")
    public IngredientDensityClassResponse assign(@PathVariable long ingredientId,
                                                 @Valid @RequestBody AssignDensityClassRequest request) {
        return densityClassService.assign(ingredientId, request);
    }
}
