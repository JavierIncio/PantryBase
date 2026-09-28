package com.pantrybase.api.catalog.web;

import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.dto.ConvertUnitsRequest;
import com.pantrybase.api.catalog.dto.UnitConversionResponse;
import com.pantrybase.api.catalog.dto.UnitResponse;
import com.pantrybase.api.catalog.service.UnitCatalogService;
import com.pantrybase.api.catalog.service.UnitConversionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for handling unit catalog and conversion requests.
 *
 * <p>Provides endpoints to list available units and convert between units.</p>
 */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitCatalogService unitService;
    private final UnitConversionService conversionService;

    public UnitController(UnitCatalogService unitService,
                          UnitConversionService conversionService) {
        this.unitService = unitService;
        this.conversionService = conversionService;
    }

    @GetMapping
    public List<UnitResponse> units() {
        return unitService.listUnits();
    }

    @PostMapping("/convert")
    public UnitConversionResponse convert(@Valid @RequestBody ConvertUnitsRequest request) {
        QuantityInfo result = conversionService.convert(
                request.amount(), request.from(), request.to(), request.ingredientCategory());
        return new UnitConversionResponse(result.amount(), result.unitCode());
    }
}
