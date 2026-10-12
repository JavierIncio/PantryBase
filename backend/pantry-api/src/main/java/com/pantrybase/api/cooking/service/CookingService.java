package com.pantrybase.api.cooking.service;

import com.pantrybase.api.cooking.domain.CookingSession;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import com.pantrybase.api.cooking.dto.CompletionDeductionResponse;
import com.pantrybase.api.cooking.dto.CookingSessionResponse;
import com.pantrybase.api.cooking.dto.CreateCookingSessionRequest;
import com.pantrybase.api.cooking.dto.DeductionLineResponse;
import com.pantrybase.api.cooking.dto.UpdateCookingSessionStateRequest;
import com.pantrybase.api.cooking.exception.CookingSessionNotFoundException;
import com.pantrybase.api.cooking.exception.CookingShortageException;
import com.pantrybase.api.cooking.exception.InvalidCookingStateTransitionException;
import com.pantrybase.api.cooking.repository.CookingSessionRepository;
import com.pantrybase.api.pantry.domain.ConsumptionOrigin;
import com.pantrybase.api.pantry.domain.PantryItem;
import com.pantrybase.api.pantry.repository.PantryItemRepository;
import com.pantrybase.api.pantry.service.PantryService;
import com.pantrybase.api.recipe.domain.Recipe;
import com.pantrybase.api.recipe.domain.RecipeIngredient;
import com.pantrybase.api.recipe.exception.RecipeNotFoundException;
import com.pantrybase.api.recipe.repository.RecipeIngredientRepository;
import com.pantrybase.api.recipe.repository.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tracks a user cooking a recipe and applies the deduction when they finish.
 *
 * <p>The interesting contract is completion. It is deliberately two-phase on the wire
 * rather than silent: a session whose recipe asks for more than the pantry holds is
 * refused with a 409 that carries the per-line plan, and only a second request that
 * accepts the shortfall commits. That keeps a completion from quietly under-deducting
 * or from pushing a line below zero, while still letting a user finish a dish they
 * rounded out without logging every extra gram.</p>
 *
 * <p>The writes it performs are only the session itself and the call into
 * {@link PantryService#deduct}: the ledger stays owned by the pantry package, so the
 * identity "the sum of an item's entries is its quantity" has a single author.</p>
 */
@Service
@Transactional(readOnly = true)
public class CookingService {

    private final CookingSessionRepository cookingSessionRepo;
    private final RecipeRepository recipeRepo;
    private final RecipeIngredientRepository recipeIngredientRepo;
    private final PantryItemRepository pantryItemRepo;
    private final PantryService pantryService;

    public CookingService(CookingSessionRepository cookingSessionRepo,
                          RecipeRepository recipeRepo,
                          RecipeIngredientRepository recipeIngredientRepo,
                          PantryItemRepository pantryItemRepo,
                          PantryService pantryService) {
        this.cookingSessionRepo = cookingSessionRepo;
        this.recipeRepo = recipeRepo;
        this.recipeIngredientRepo = recipeIngredientRepo;
        this.pantryItemRepo = pantryItemRepo;
        this.pantryService = pantryService;
    }

    /**
     * Opens a session on a recipe the caller can see.
     *
     * <p>No quantity is recorded here: the amounts to deduct live in the recipe and may
     * be edited (or the recipe refreshed) before the session is completed, so pinning
     * them now would mean deducting a copy of a decision that has not been made yet.</p>
     *
     * @throws RecipeNotFoundException if the recipe does not exist
     */
    @Transactional
    public CookingSessionResponse create(Long userId, CreateCookingSessionRequest request) {
        Recipe recipe = recipeRepo.findById(request.recipeId())
                .orElseThrow(() -> new RecipeNotFoundException(request.recipeId()));

        CookingSession session = new CookingSession();
        session.setUserId(userId);
        session.setRecipeId(recipe.getId());
        session.setStatus(CookingSessionStatus.IN_PROGRESS);

        return toResponse(cookingSessionRepo.save(session), null);
    }

    /**
     * The caller's sessions, most recent first.
     *
     * <p>Reads are owner-scoped like every other cooking query, and the deduction is left
     * off: a history view shows what happened by state and time, and replaying a past
     * deduction on a list read would mean rebuilding a plan the response does not need.</p>
     */
    public List<CookingSessionResponse> list(Long userId) {
        return cookingSessionRepo.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(session -> toResponse(session, null))
                .toList();
    }

    /**
     * One session, so the client can reload a running one by id.
     *
     * @throws CookingSessionNotFoundException if the session is not this user's
     */
    public CookingSessionResponse get(Long userId, long sessionId) {
        CookingSession session = cookingSessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CookingSessionNotFoundException(sessionId));

        return toResponse(session, null);
    }

    /**
     * Moves a session to a new state, deducting the recipe when it is completed.
     *
     * <p>Both terminal states stamp {@code completedAt}, so a session's duration is the
     * gap between its two timestamps for finished and abandoned runs alike.</p>
     *
     * @throws CookingSessionNotFoundException       if the session is not this user's
     * @throws InvalidCookingStateTransitionException if the move is not in the machine
     * @throws RecipeNotFoundException                if the recipe has since vanished
     * @throws CookingShortageException               if completion exceeds the pantry and
     *                                                the shortfall is not accepted
     */
    @Transactional
    public CookingSessionResponse updateState(Long userId, long sessionId,
                                              UpdateCookingSessionStateRequest request) {
        CookingSession session = cookingSessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CookingSessionNotFoundException(sessionId));

        CookingSessionStatus next = request.status();
        if (!canMove(session.getStatus(), next)) {
            throw new InvalidCookingStateTransitionException(session.getStatus(), next);
        }

        if (next == CookingSessionStatus.COMPLETED) {
            boolean acceptShortage = Boolean.TRUE.equals(request.acceptShortage());
            CompletionDeductionResponse deduction = complete(session, acceptShortage);
            return toResponse(session, deduction);
        }

        session.setStatus(next);
        if (next == CookingSessionStatus.ABANDONED) {
            session.setCompletedAt(Instant.now());
        }

        return toResponse(cookingSessionRepo.save(session), null);
    }

    /**
     * The state machine. A session runs while it is open, ends once, and cannot leave a
     * terminal state — repeating a completion would deduct a second time.
     */
    private boolean canMove(CookingSessionStatus from, CookingSessionStatus to) {
        return switch (from) {
            case IN_PROGRESS -> to == CookingSessionStatus.PAUSED
                    || to == CookingSessionStatus.COMPLETED
                    || to == CookingSessionStatus.ABANDONED;
            case PAUSED -> to == CookingSessionStatus.IN_PROGRESS
                    || to == CookingSessionStatus.COMPLETED
                    || to == CookingSessionStatus.ABANDONED;
            case COMPLETED, ABANDONED -> false;
        };
    }

    /**
     * Builds the deduction plan, refuses a shortfall unless accepted, and applies it.
     *
     * <p>Only matched lines with a canonical amount can be deducted: an unmatched line has
     * no catalog id to charge and a line without a canonical amount has no comparable
     * quantity, which is exactly the honesty rule H4 materialisation records. A shortage
     * is any line whose amount exceeds what the pantry holds; when accepted, each short
     * line is taken down to zero and the remainder is treated as contributed off the
     * books.</p>
     */
    private CompletionDeductionResponse complete(CookingSession session, boolean acceptShortage) {
        Recipe recipe = recipeRepo.findById(session.getRecipeId())
                .orElseThrow(() -> new RecipeNotFoundException(session.getRecipeId()));

        List<RecipeIngredient> lines = recipeIngredientRepo
                .findByRecipeIdOrderByPositionAsc(recipe.getId()).stream()
                .filter(line -> Boolean.TRUE.equals(line.getMatched())
                        && line.getIngredientId() != null
                        && line.getCanonicalAmount() != null)
                .toList();

        Map<Long, PantryItem> stock = stockByIngredient(session.getUserId(), lines);

        List<DeductionLineResponse> plan = lines.stream().map(line -> {
            PantryItem item = stock.get(line.getIngredientId());
            BigDecimal available = item == null ? BigDecimal.ZERO : item.getQuantity();
            BigDecimal deducted = available.min(line.getCanonicalAmount());

            return new DeductionLineResponse(
                    line.getIngredientId(),
                    line.getIngredientName(),
                    line.getCanonicalUnitCode(),
                    line.getCanonicalAmount(),
                    available,
                    deducted,
                    available.subtract(deducted));
        }).toList();

        boolean hasShortage = plan.stream()
                .anyMatch(line -> line.available().compareTo(line.needed()) < 0);
        if (hasShortage && !acceptShortage) {
            throw new CookingShortageException(plan);
        }

        plan.stream()
                .filter(line -> line.deducted().signum() > 0)
                .forEach(line -> pantryService.deduct(session.getUserId(), line.ingredientId(),
                        line.deducted(), ConsumptionOrigin.COOKING_SESSION));

        session.setStatus(CookingSessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        cookingSessionRepo.save(session);

        return new CompletionDeductionResponse(hasShortage, plan);
    }

    private Map<Long, PantryItem> stockByIngredient(Long userId, List<RecipeIngredient> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }

        List<Long> ingredientIds = lines.stream()
                .map(RecipeIngredient::getIngredientId)
                .toList();

        return pantryItemRepo.findByUserIdAndIngredientIdIn(userId, ingredientIds).stream()
                .collect(Collectors.toMap(PantryItem::getIngredientId, Function.identity()));
    }

    private CookingSessionResponse toResponse(CookingSession session, CompletionDeductionResponse deduction) {
        return new CookingSessionResponse(
                session.getId(),
                session.getRecipeId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getCompletedAt(),
                deduction);
    }
}