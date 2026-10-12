package com.pantrybase.api.cooking.domain;

/**
 * The life of a {@link CookingSession}, as a state machine.
 *
 * <p>Only two transitions require anything of the record: completing a session
 * deducts the recipe from the pantry (see the service), and every terminal state
 * stamps {@code completed_at}. The map a reader needs is: a session starts
 * {@link #IN_PROGRESS}, may pause and resume any number of times, and ends either
 * finished or abandoned — never both.</p>
 */
public enum CookingSessionStatus {
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    ABANDONED
}