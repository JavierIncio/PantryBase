package com.pantrybase.api.catalog.exception;

public class MeasureConversionNotFoundException extends RuntimeException {
    public MeasureConversionNotFoundException(String unitCode) {
        super("No measure conversion found for unit code: " + unitCode);
    }
}
