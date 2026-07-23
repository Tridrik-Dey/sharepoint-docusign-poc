package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends PoEnvelopeException {

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
