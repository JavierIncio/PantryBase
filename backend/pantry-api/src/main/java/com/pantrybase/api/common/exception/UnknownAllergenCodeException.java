package com.pantrybase.api.common.exception;

import java.util.List;

/**
 * Exception thrown when an unknown allergen code is encountered.
 */
public class UnknownAllergenCodeException extends RuntimeException {
    public UnknownAllergenCodeException(List<String> codes) {
        super(String.format("Unknown allergen code: %s", codes));
    }
}
