package com.example.sharepointdocusign.dto;

/**
 * Generic error payload used for failures that occur before PO context
 * (poNumber/revision) is available, e.g. malformed multipart requests.
 */
public record ErrorResponse(boolean success, String errorCode, String message, String correlationId) {

    public static ErrorResponse of(String errorCode, String message, String correlationId) {
        return new ErrorResponse(false, errorCode, message, correlationId);
    }
}
