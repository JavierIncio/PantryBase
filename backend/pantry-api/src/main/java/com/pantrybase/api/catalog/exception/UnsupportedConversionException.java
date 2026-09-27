package com.pantrybase.api.catalog.exception;

public class UnsupportedConversionException extends RuntimeException {
    public UnsupportedConversionException(String fromCategory, String toCategory) {
        super("Unsupported conversion from " +
                fromCategory + " to " + toCategory);
    }
}
