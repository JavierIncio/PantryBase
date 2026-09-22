package com.pantrybase.api.user.repository;

import com.pantrybase.api.user.domain.Allergen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Lookup access to the allergen ontology: seeded by migrations,
 * never created or mutated at runtime.
 */
public interface AllergenRepository extends JpaRepository<Allergen, Short> {
    List<Allergen> findByCodeIn(Collection<String> codes);
}
