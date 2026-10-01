package com.pantrybase.api.catalog.exception;

public class DensityClassNotFoundException extends RuntimeException {
    public DensityClassNotFoundException(String code) {
        super("No density established for density class: " + code);
    }
}
