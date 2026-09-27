package com.pantrybase.api.catalog.repository;

import com.pantrybase.api.catalog.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, String> {
    Optional<Unit> findByCode(String code);
}
