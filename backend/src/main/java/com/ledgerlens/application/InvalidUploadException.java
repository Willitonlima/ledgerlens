package com.ledgerlens.application;

import org.springframework.http.HttpStatus;

public class InvalidUploadException extends RuntimeException {

    private final HttpStatus status;

    public InvalidUploadException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
