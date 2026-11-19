package com.urlshortener.web;

import org.springframework.http.HttpStatus;

/** Base type for errors that map cleanly to an HTTP status and a stable error type. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorType;

    protected ApiException(HttpStatus status, String errorType, String message) {
        super(message);
        this.status = status;
        this.errorType = errorType;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorType() {
        return errorType;
    }
}
