package com.pantrybase.api.catalog.repository;

import com.pantrybase.api.catalog.domain.IngredientDensity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientDensityRepository extends JpaRepository<IngredientDensity, String> {}