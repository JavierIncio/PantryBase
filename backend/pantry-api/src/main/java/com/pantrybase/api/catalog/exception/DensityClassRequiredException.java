package com.pantrybase.api.catalog.exception;

public class DensityClassRequiredException extends RuntimeException {
    public DensityClassRequiredException(String from, String to) {
        super(String.format("A densityClass is required to convert from %s to %s, "
                + "because this ingredient has no captured measure and no assigned class.", from, to));
    }
}
