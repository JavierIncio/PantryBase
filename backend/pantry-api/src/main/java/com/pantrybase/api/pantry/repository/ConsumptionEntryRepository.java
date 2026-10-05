package com.pantrybase.api.pantry.repository;

import com.pantrybase.api.pantry.domain.ConsumptionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsumptionEntryRepository extends JpaRepository<ConsumptionEntry, Long> {

    List<ConsumptionEntry> findByPantryItemIdOrderByCreatedAtAsc(Long pantryItemId);
}