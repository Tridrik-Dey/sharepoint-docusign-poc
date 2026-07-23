package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

public class TooManyDocumentsException extends PoEnvelopeException {

    public TooManyDocumentsException(String message) {
        super("TOO_MANY_DOCUMENTS", message, HttpStatus.BAD_REQUEST);
    }
}
