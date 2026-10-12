package com.pantrybase.api.pantry.domain;

/**
 * What caused a quantity movement to be written to {@link ConsumptionEntry}.
 *
 * <p>Exists so a later reader can tell a deliberate user action from an automatic one.
 * H3 only records the former; H5 adds the deduction made when a cooking session
 * completes, at which point the two are worth distinguishing.</p>
 */
public enum ConsumptionOrigin {
    /**
     * The user adjusted the line, or added it to the pantry in the first place.
     *
     * <p>The opening balance shares this origin rather than having one of its own: it is
     * the user telling us what they have, and splitting it would double the cases a
     * reader has to handle for no fact gained. A manual deduction from the inventory
     * keeps this origin too: it is as deliberate an action as any other adjustment.</p>
     */
    MANUAL,

    /**
     * A quantity deducted automatically because a cooking session was completed.
     *
     * <p>Distinct from {@link #MANUAL} so the ledger can tell a movement the session
     * wrote from one the user typed: the former is the whole recipe's deduction in one
     * commit, and answering "why did three lines drop at once" is the pair of that
     * question with an answer.</p>
     */
    COOKING_SESSION
}