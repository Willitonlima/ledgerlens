package com.ledgerlens.application;

import java.util.List;

public class CsvValidationException extends RuntimeException {

    private final List<CsvError> errors;
    private final int totalErrors;

    public CsvValidationException(String message, List<CsvError> errors, int totalErrors) {
        super(message);
        this.errors = List.copyOf(errors);
        this.totalErrors = totalErrors;
    }

    public List<CsvError> getErrors() {
        return errors;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public record CsvError(int line, String field, String message) {
    }
}
