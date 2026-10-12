package com.pantrybase.api.cooking.dto;

/**
 * How a user's cooking sessions have gone so far, counted by state and timed.
 *
 * <p>A read-only aggregate of the caller's own sessions, like the pantry summary: the
 * counts are folded from one read of the rows, so {@link #totalSessions} is the sum of the
 * four state counts and cannot disagree with them.</p>
 *
 * <p>{@link #completionRate} is the share of <em>finished</em> sessions that were completed,
 * not of all sessions. A session still in progress is not a failure, and counting it in the
 * denominator would make the rate drop just because the user has a dish on the stove; the
 * rate answers "of the runs that ended, how many ended as a meal", so only the two terminal
 * states take part. It is {@code null} when nothing has finished yet, because a rate over an
 * empty set is not zero but undefined.</p>
 *
 * <p>{@link #averageDurationSeconds} averages the gap between a session starting and its
 * terminal stamp (completion or abandonment alike; see {@code CookingSession.completedAt}).
 * It is {@code null} for the same reason — no finished run, no average.</p>
 *
 * @param totalSessions          every session the user has opened
 * @param inProgressSessions     sessions currently running
 * @param pausedSessions         sessions put on hold
 * @param completedSessions      sessions finished as a meal
 * @param abandonedSessions      sessions given up on
 * @param averageDurationSeconds mean seconds from start to finish, or null if none finished
 * @param completionRate         completed share of finished sessions, or null if none finished
 */
public record CookingSummaryResponse(
        long totalSessions,
        long inProgressSessions,
        long pausedSessions,
        long completedSessions,
        long abandonedSessions,
        Double averageDurationSeconds,
        Double completionRate
) {}
