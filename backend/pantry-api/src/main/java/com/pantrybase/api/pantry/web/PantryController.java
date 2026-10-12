package com.pantrybase.api.pantry.web;

import com.pantrybase.api.pantry.dto.AdjustPantryItemRequest;
import com.pantrybase.api.pantry.dto.ConsumePantryItemRequest;
import com.pantrybase.api.pantry.dto.ConsumptionEntryResponse;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.pantry.dto.PantrySummaryResponse;
import com.pantrybase.api.pantry.dto.UpdatePantryItemRequest;
import com.pantrybase.api.pantry.service.PantryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The authenticated user's own inventory.
 *
 * <p>Every handler takes the owner from the authentication principal rather than from
 * the request, so there is no path, body or parameter a caller can use to reach another
 * user's pantry.</p>
 *
 * <p>There is no read-by-id: the list already carries every field a line is displayed
 * with, so a per-item GET would exist only for a client that has not been written yet.</p>
 */
@RestController
@RequestMapping("/api/pantry")
public class PantryController {

    private final PantryService pantryService;

    public PantryController(PantryService pantryService) {
        this.pantryService = pantryService;
    }

    @GetMapping
    public List<PantryItemResponse> list(@AuthenticationPrincipal Long userId) {
        return pantryService.list(userId);
    }

    /**
     * The size of the pantry, counted by category and by kind of unit.
     *
     * <p>A fixed segment rather than a path variable, which is what lets it sit at
     * {@code /summary} instead of {@code /{itemId}/summary}: the ids are numbers, so a
     * request for the literal word could not be one of them, but nothing about that is
     * enforced and a future numeric segment would make the routing ambiguous.</p>
     *
     * <p>Separate from the list rather than a field of it, because it answers a different
     * question: the list is what the user has, this is what they have overall, and the
     * counts have to stay correct even if the list is ever given a page of its own.</p>
     */
    @GetMapping("/summary")
    public PantrySummaryResponse summary(@AuthenticationPrincipal Long userId) {
        return pantryService.summary(userId);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public PantryItemResponse create(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody CreatePantryItemRequest request) {
        return pantryService.create(userId, request);
    }

    /**
     * Replaces the editable fields of a line.
     *
     * <p>A PUT and not a PATCH because it replaces both of them; an update that merged
     * with whatever was stored would leave the caller unable to clear a field by leaving
     * it out.</p>
     */
    @PutMapping("/{itemId}")
    public PantryItemResponse update(@AuthenticationPrincipal Long userId,
                                     @PathVariable long itemId,
                                     @Valid @RequestBody UpdatePantryItemRequest request) {
        return pantryService.update(userId, itemId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{itemId}")
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long itemId) {
        pantryService.delete(userId, itemId);
    }

    /**
     * Moves an item's quantity by a signed amount.
     *
     * <p>A sub-resource and not a PATCH on the line itself, because it creates a ledger
     * entry rather than editing fields, and because a merge patch on the line would put
     * the quantity back within reach of an endpoint that has no way to record the
     * movement it made.</p>
     */
    @PostMapping("/{itemId}/adjustments")
    public PantryItemResponse adjust(@AuthenticationPrincipal Long userId,
                                     @PathVariable long itemId,
                                     @Valid @RequestBody AdjustPantryItemRequest request) {
        return pantryService.adjust(userId, itemId, request);
    }

    @GetMapping("/{itemId}/entries")
    public List<ConsumptionEntryResponse> entries(@AuthenticationPrincipal Long userId,
                                                 @PathVariable long itemId) {
        return pantryService.entries(userId, itemId);
    }

    /**
     * Consumes an amount of a line, without a cooking session.
     *
     * <p>A sibling of {@code /adjustments} rather than a special value of it: an
     * adjustment takes a signed amount and can add stock, while this endpoint exists to
     * record that the user used something up and refuses any amount that is not positive.
     * The movement is written to the same ledger, so the item's quantity stays
     * explained.</p>
     */
    @PostMapping("/{itemId}/consume")
    public PantryItemResponse consume(@AuthenticationPrincipal Long userId,
                                      @PathVariable long itemId,
                                      @Valid @RequestBody ConsumePantryItemRequest request) {
        return pantryService.consume(userId, itemId, request);
    }
}