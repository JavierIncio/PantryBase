package com.pantrybase.api.pantry.service;

import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.domain.UnitConverter;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.pantry.domain.ConsumptionEntry;
import com.pantrybase.api.pantry.domain.ConsumptionOrigin;
import com.pantrybase.api.pantry.domain.PantryItem;
import com.pantrybase.api.pantry.dto.AdjustPantryItemRequest;
import com.pantrybase.api.pantry.dto.ConsumptionEntryResponse;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.pantry.dto.UpdatePantryItemRequest;
import com.pantrybase.api.pantry.exception.IngredientAlreadyInPantryException;
import com.pantrybase.api.pantry.exception.IngredientNotInCatalogException;
import com.pantrybase.api.pantry.exception.InsufficientStockException;
import com.pantrybase.api.pantry.exception.PantryItemNotFoundException;
import com.pantrybase.api.pantry.exception.ZeroAdjustmentException;
import com.pantrybase.api.pantry.repository.ConsumptionEntryRepository;
import com.pantrybase.api.pantry.repository.PantryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 *
 * <p>Every movement of a quantity writes a {@link ConsumptionEntry}, including the one
 * that creates the line. That is what makes the sum of an item's entries equal its
 * quantity, an identity the tests assert rather than a convention the code follows.</p>
 */
@Service
@Transactional(readOnly = true)
public class PantryService {

    private final PantryItemRepository pantryItemRepo;
    private final ConsumptionEntryRepository consumptionEntryRepo;
    private final IngredientRepository ingredientRepo;
    private final UnitConverter unitConverter;

    public PantryService(PantryItemRepository pantryItemRepo,
                         ConsumptionEntryRepository consumptionEntryRepo,
                         IngredientRepository ingredientRepo,
                         UnitConverter unitConverter) {
        this.pantryItemRepo = pantryItemRepo;
        this.consumptionEntryRepo = consumptionEntryRepo;
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
     * <p>The opening entry is written here rather than on first adjustment, so that the
     * identity "the sum of an item's entries is its quantity" already holds for a line
     * nobody has touched yet. Same transaction, which means the ledger cannot end up with
     * a line that has no way to explain its own quantity.</p>
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

        PantryItem saved = pantryItemRepo.save(item);
        recordEntry(saved, stored.amount());

        return toResponse(saved, ingredient);
    }

    /**
     * Moves an item's quantity by a signed amount and records the movement.
     *
     * <p>The amount is converted into the unit the line already stores rather than into
     * whatever the caller sent, because the unit of a line is immutable: reinterpreting
     * the whole history under a new unit would silently change the meaning of every
     * entry written before it.</p>
     *
     * <p>A result below zero is refused with {@link InsufficientStockException}. Exactly
     * zero is allowed, so a caller may trust that a successful adjustment leaves stock
     * they have, or none at all — never a deficit they would cook against.</p>
     *
     * <p>The entry is written before the item is flushed, inside the same transaction, so
     * a delta can never outlive the quantity it explains. A writer who loaded the line
     * first loses the race through {@code @Version} and the whole transaction rolls back
     * with it, entry included.</p>
     *
     * @throws PantryItemNotFoundException   if the item is not in this user's pantry
     * @throws ZeroAdjustmentException       if the amount is zero
     * @throws InsufficientStockException    if the result would not leave stock on the shelf
     */
    @Transactional
    public PantryItemResponse adjust(Long userId, long itemId, AdjustPantryItemRequest request) {
        PantryItem item = requireOwned(userId, itemId);

        if (request.amount().signum() == 0) {
            throw new ZeroAdjustmentException();
        }

        QuantityInfo delta = unitConverter.convert(
                request.amount(), request.unitCode(), item.getUnitCode(), item.getIngredientId(), null);

        BigDecimal result = item.getQuantity().add(delta.amount());
        if (result.signum() < 0) {
            // Strictly below zero, not zero or below: running out of an ingredient is a
            // normal outcome, and BigDecimal.signum() returns 0 for an exact zero, so a
            // "<= 0" test here would refuse precisely the case that has to work.
            throw new InsufficientStockException(itemId, delta.amount(), item.getQuantity(), result, item.getUnitCode());
        }

        item.setQuantity(result);
        PantryItem saved = pantryItemRepo.save(item);
        recordEntry(saved, delta.amount());

        return toResponse(saved,
                ingredientRepo.getReferenceById(item.getIngredientId()));
    }

    /**
     * The movements that explain an item's quantity, oldest first.
     *
     * <p>Reading the ledger is what makes it an audit trail rather than a table nobody
     * queries, and it is the only way to check the identity the whole design rests on.</p>
     *
     * <p>Scoped by the owner of the item, not by the entry's own user id: the entries of
     * a line are only reachable through that line, so requiring ownership first is both
     * cheaper and impossible to get wrong by forgetting a condition.</p>
     *
     * @throws PantryItemNotFoundException if the item is not in this user's pantry
     */
    public List<ConsumptionEntryResponse> entries(Long userId, long itemId) {
        requireOwned(userId, itemId);

        return consumptionEntryRepo.findByPantryItemIdOrderByCreatedAtAsc(itemId).stream()
                .map(entry -> new ConsumptionEntryResponse(
                        entry.getId(),
                        entry.getDeltaQuantity(),
                        entry.getOrigin(),
                        entry.getCreatedAt()))
                .toList();
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

    /**
     * Writes one movement of stock into the ledger.
     *
     * <p>Takes the saved item because the entry needs its id, which only exists after
     * the insert; taking the delta separately keeps the caller from having to restate a
     * number that is already in hand.</p>
     */
    private void recordEntry(PantryItem item, BigDecimal delta) {
        ConsumptionEntry entry = new ConsumptionEntry();
        entry.setPantryItemId(item.getId());
        entry.setUserId(item.getUserId());
        entry.setDeltaQuantity(delta);
        entry.setOrigin(ConsumptionOrigin.MANUAL);

        consumptionEntryRepo.save(entry);
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