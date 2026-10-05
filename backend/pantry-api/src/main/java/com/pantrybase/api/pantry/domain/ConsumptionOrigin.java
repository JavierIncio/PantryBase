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
     * reader has to handle for no fact gained.</p>
     */
    MANUAL
}