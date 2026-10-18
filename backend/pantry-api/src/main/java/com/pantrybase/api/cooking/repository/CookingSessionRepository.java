package com.pantrybase.api.cooking.repository;

import com.pantrybase.api.cooking.domain.CookingSession;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** One time a user cooks one recipe. */
public interface CookingSessionRepository extends JpaRepository<CookingSession, Long> {

    /**
     * Every read is scoped to the owner, mirroring the pantry habits: there is no
     * id-only finder to reach for by mistake, so another user's session id can only
     * ever answer 404.
     */
    Optional<CookingSession> findByIdAndUserId(Long id, Long userId);

    List<CookingSession> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Counts the sessions an operator would call "stuck": still open and started before
     * {@code startedBefore}. {@code startedAt} rather than a modification timestamp — the
     * entity deliberately has no {@code updatedAt}, and a cook who walks away leaves no
     * later stamp anyway.
     */
    long countByStatusAndStartedAtBefore(CookingSessionStatus status, Instant startedBefore);
}