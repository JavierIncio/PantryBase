package com.pantrybase.api.cooking.service;

import com.pantrybase.api.cooking.domain.CookingSession;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import com.pantrybase.api.cooking.dto.CompletionDeductionResponse;
import com.pantrybase.api.cooking.dto.CookingSessionResponse;
import com.pantrybase.api.cooking.dto.CookingSummaryResponse;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
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

    /**
     * Counters the summary can also be read from without querying the database.
     *
     * <p>The two terminal outcomes each get a counter and the gap to the terminal stamp is
     * timed, so an operator can watch completions and how long a cook takes on a dashboard
     * even between API calls. They are process-lifetime totals, unlike the summary, which is
     * per-user and read from the rows: the counters say how the server is being used, the
     * summary says how one user's kitchen is going.</p>
     */
    private final Counter completedCounter;
    private final Counter abandonedCounter;
    private final Timer sessionDuration;

    public CookingService(CookingSessionRepository cookingSessionRepo,
                          RecipeRepository recipeRepo,
                          RecipeIngredientRepository recipeIngredientRepo,
                          PantryItemRepository pantryItemRepo,
                          PantryService pantryService,
                          MeterRegistry meterRegistry) {
        this.cookingSessionRepo = cookingSessionRepo;
        this.recipeRepo = recipeRepo;
        this.recipeIngredientRepo = recipeIngredientRepo;
        this.pantryItemRepo = pantryItemRepo;
        this.pantryService = pantryService;
        this.completedCounter = meterRegistry.counter("pantry.cooking.sessions.completed");
        this.abandonedCounter = meterRegistry.counter("pantry.cooking.sessions.abandoned");
        this.sessionDuration = Timer.builder("pantry.cooking.sessions.duration")
                .description("Time from a cooking session starting to finishing or being abandoned")
                .register(meterRegistry);
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
     * The caller's sessions folded into counts, an average duration and a completion rate.
     *
     * <p>Folded from one read of the user's rows rather than from several count queries, so
     * the total and the per-state counts are the same numbers by construction. That matters
     * more here than the size of the read: sessions accumulate slowly (a person cooks a
     * handful of meals, not thousands), whereas a second aggregate could disagree with the
     * first for no reason a client could resolve.</p>
     *
     * <p>Only sessions with a terminal stamp feed the duration; a running session has no end
     * yet and no say in the average. The completion rate is over the finished sessions only,
     * so an open session does not move it (see {@link CookingSummaryResponse}).</p>
     */
    public CookingSummaryResponse summary(Long userId) {
        long inProgress = 0;
        long paused = 0;
        long completed = 0;
        long abandoned = 0;
        long timedSessions = 0;
        double totalDurationSeconds = 0;

        for (CookingSession session : cookingSessionRepo.findAllByUserIdOrderByCreatedAtDesc(userId)) {
            switch (session.getStatus()) {
                case IN_PROGRESS -> inProgress++;
                case PAUSED -> paused++;
                case COMPLETED -> completed++;
                case ABANDONED -> abandoned++;
            }

            if (session.getCompletedAt() != null) {
                totalDurationSeconds += Duration.between(session.getStartedAt(),
                        session.getCompletedAt()).toSeconds();
                timedSessions++;
            }
        }

        long finished = completed + abandoned;

        return new CookingSummaryResponse(
                inProgress + paused + completed + abandoned,
                inProgress,
                paused,
                completed,
                abandoned,
                timedSessions == 0 ? null : totalDurationSeconds / timedSessions,
                finished == 0 ? null : (double) completed / finished);
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
            completedCounter.increment();
            recordDuration(session);
            return toResponse(session, deduction);
        }

        session.setStatus(next);
        if (next == CookingSessionStatus.ABANDONED) {
            session.setCompletedAt(Instant.now());
            abandonedCounter.increment();
            recordDuration(session);
        }

        return toResponse(cookingSessionRepo.save(session), null);
    }

    /**
     * Feeds the duration timer, which both terminal states share.
     *
     * <p>Times the gap between the two stamps rather than the wall clock at call time, so
     * the figure is the session's own elapsed span — pauses included, because a session put
     * on hold and finished later did take that long to finish.</p>
     */
    private void recordDuration(CookingSession session) {
        sessionDuration.record(Duration.between(session.getStartedAt(), session.getCompletedAt()));
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