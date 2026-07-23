package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

public class DocumentTooLargeException extends PoEnvelopeException {

    public DocumentTooLargeException(String message) {
        super("DOCUMENT_TOO_LARGE", message, HttpStatus.BAD_REQUEST);
    }
}
