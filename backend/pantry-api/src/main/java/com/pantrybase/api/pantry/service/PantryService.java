package com.pantrybase.api.pantry.service;

import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.domain.UnitConverter;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.pantry.domain.PantryItem;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.pantry.dto.UpdatePantryItemRequest;
import com.pantrybase.api.pantry.exception.IngredientAlreadyInPantryException;
import com.pantrybase.api.pantry.exception.IngredientNotInCatalogException;
import com.pantrybase.api.pantry.exception.PantryItemNotFoundException;
import com.pantrybase.api.pantry.repository.PantryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the user's inventory: what they have, in the unit it is kept in.
 *
 * <p>Every method takes the owner as an argument rather than resolving it from the
 * security context, so there is exactly one place a user id enters the package and no
 * read or write can be written without one.</p>
 *
 * <p>The quantity is normalized on the way in and never on the way out. Storing what the
 * user typed would mean every later comparison (recipe coverage in H4, the deduction in
 * H5) had to convert first, against a density that the ingredient may not have.</p>
 */
@Service
@Transactional(readOnly = true)
public class PantryService {

    private final PantryItemRepository pantryItemRepo;
    private final IngredientRepository ingredientRepo;
    private final UnitConverter unitConverter;

    public PantryService(PantryItemRepository pantryItemRepo,
                         IngredientRepository ingredientRepo,
                         UnitConverter unitConverter) {
        this.pantryItemRepo = pantryItemRepo;
        this.ingredientRepo = ingredientRepo;
        this.unitConverter = unitConverter;
    }

    /**
     * The whole pantry of one user, ordered by category and name.
     *
     * <p>Read as one query with the ingredient already joined, because the response needs
     * the food's name and category and a per-line lookup would make a single request
     * into N+1 on the view that is opened most often.</p>
     */
    public List<PantryItemResponse> list(Long userId) {
        return pantryItemRepo.findAllWithIngredientByUserId(userId).stream()
                .map(row -> toResponse((PantryItem) row[0], (Ingredient) row[1]))
                .toList();
    }

    /**
     * Adds an ingredient, resolving the amount to the unit the pantry stores it in.
     *
     * <p>The order of the three steps is deliberate. The ingredient is resolved first
     * because a volume only becomes a weight through that very ingredient's density or
     * density class, so the conversion cannot happen before it is known. The duplicate
     * check comes before the conversion, not after: an existing line is the fact the
     * caller cannot see, and telling them to use the adjustment is more useful than
     * complaining about anything else in a request they are about to redo.</p>
     *
     * @throws IngredientNotInCatalogException    if the food is not in the local catalog
     * @throws IngredientAlreadyInPantryException if the user already has this ingredient
     */
    @Transactional
    public PantryItemResponse create(Long userId, CreatePantryItemRequest request) {
        Ingredient ingredient = ingredientRepo.findByFdcId(request.fdcId())
                .orElseThrow(() -> new IngredientNotInCatalogException(request.fdcId()));

        if (pantryItemRepo.existsByUserIdAndIngredientId(userId, ingredient.getId())) {
            throw new IngredientAlreadyInPantryException(request.fdcId());
        }

        QuantityInfo stored = unitConverter.toStorageUnit(
                request.amount(), request.unitCode(), ingredient.getId());

        PantryItem item = new PantryItem();
        item.setUserId(userId);
        item.setIngredientId(ingredient.getId());
        item.setQuantity(stored.amount());
        item.setUnitCode(stored.unitCode());
        item.setLocation(request.location());
        item.setNote(request.note());

        return toResponse(pantryItemRepo.save(item), ingredient);
    }

    /**
     * Replaces the location and note of one item.
     *
     * <p>Neither the quantity nor the unit is touched; a caller wanting to move stock
     * has to go through an adjustment, which is what keeps the ledger complete.</p>
     *
     * <p>Saving an unchanged line still bumps its version, because the version is meant
     * to count attempts rather than to detect them precisely. That is the safe direction:
     * a version that moved forward makes the next writer re-read instead of overwriting.</p>
     *
     * @throws PantryItemNotFoundException if the item is not in this user's pantry
     */
    @Transactional
    public PantryItemResponse update(Long userId, long itemId, UpdatePantryItemRequest request) {
        PantryItem item = requireOwned(userId, itemId);

        item.setLocation(request.location());
        item.setNote(request.note());

        return toResponse(pantryItemRepo.save(item),
                ingredientRepo.getReferenceById(item.getIngredientId()));
    }

    /**
     * Removes a line from the pantry, together with the entries that explain it.
     *
     * <p>Deliberate, not an oversight: an entry whose item no longer exists cannot be
     * shown next to anything, and keeping the orphan would only make a later deletion
     * impossible without a second endpoint whose sole purpose is that.</p>
     *
     * @throws PantryItemNotFoundException if the item is not in this user's pantry
     */
    @Transactional
    public void delete(Long userId, long itemId) {
        pantryItemRepo.delete(requireOwned(userId, itemId));
    }

    /**
     * Loads an item scoped to its owner.
     *
     * <p>Scoping in the query rather than after the read is what turns another user's id
     * into a 404 instead of a successful read.</p>
     */
    private PantryItem requireOwned(Long userId, long itemId) {
        return pantryItemRepo.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new PantryItemNotFoundException(itemId));
    }

    private PantryItemResponse toResponse(PantryItem item, Ingredient ingredient) {
        return new PantryItemResponse(
                item.getId(),
                ingredient.getFdcId(),
                ingredient.getName(),
                ingredient.getCategory(),
                item.getQuantity(),
                item.getUnitCode(),
                item.getLocation(),
                item.getNote(),
                item.getVersion(),
                item.getUpdatedAt());
    }
}