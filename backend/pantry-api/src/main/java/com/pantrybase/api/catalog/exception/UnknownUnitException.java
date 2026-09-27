package com.pantrybase.api.catalog.exception;

public class UnknownUnitException extends RuntimeException {
    public UnknownUnitException(String code) {
        super("Unknown unit code: " + code);
    }
}
