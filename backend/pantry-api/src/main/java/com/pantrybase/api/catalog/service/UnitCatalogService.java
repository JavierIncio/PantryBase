package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.Unit;
import com.pantrybase.api.catalog.dto.UnitResponse;
import com.pantrybase.api.catalog.repository.UnitRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Service class for managing the unit catalog.
 *
 * <p>Transactional read-only operations to omit dirty-checking and improve performance.</p>
 */
@Service
@Transactional(readOnly = true)
public class UnitCatalogService {

    private final UnitRepository unitRepo;

    public UnitCatalogService(UnitRepository unitRepo) {
        this.unitRepo = unitRepo;
    }

    /** Retrieves a list of all units in the catalog, sorted by their code. */
    public List<UnitResponse> listUnits() {
        return unitRepo.findAll(Sort.by("code")).stream()
                .map(this::toUnitResponse)
                .toList();
    }

    private UnitResponse toUnitResponse(Unit unit) {
        return new UnitResponse(
                unit.getCode(), unit.getName(),
                unit.getCategory(), unit.isCanonical());
    }
}
