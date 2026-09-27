package com.pantrybase.api.catalog.repository;

import com.pantrybase.api.catalog.domain.MeasureConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeasureConversionRepository extends JpaRepository<MeasureConversion, String> {
    Optional<MeasureConversion> findByUnitCode(String code);
}
