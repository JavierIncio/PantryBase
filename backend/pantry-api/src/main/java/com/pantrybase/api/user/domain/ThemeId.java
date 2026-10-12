package com.pantrybase.api.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * The UI theme a user has chosen, one per palette the frontend compiles.
 *
 * <p>The wire value is the lower-case id — the same string the client uses as the
 * {@code data-theme} attribute — not the Java constant name, so the API value is the CSS
 * selector itself and the client can apply it without translating it. The database still
 * stores the enum name via {@code @Enumerated(STRING)}; only the JSON differs.</p>
 */
public enum ThemeId {
    BLUE, GREEN, AMBER, ROSE, NEUTRAL;

    /** The lower-case id shared by the API and the frontend's CSS selector. */
    @JsonValue
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses the lower-case id back into the enum.
     *
     * <p>A value with no matching palette — including a differently-cased one that is not
     * simply upper-case — fails here and is rejected as a 400 by the API, so an unknown
     * theme can never be stored.</p>
     */
    @JsonCreator
    public static ThemeId fromId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
