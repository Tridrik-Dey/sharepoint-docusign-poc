package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all business/integration failures raised while handling
 * POST /api/v1/po-envelopes. Each subclass carries the stable errorCode
 * returned to API consumers and the HTTP status to answer with.
 */
public abstract class PoEnvelopeException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected PoEnvelopeException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected PoEnvelopeException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
