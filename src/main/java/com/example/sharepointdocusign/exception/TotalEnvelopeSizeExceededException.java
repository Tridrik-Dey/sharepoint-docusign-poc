package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

public class TotalEnvelopeSizeExceededException extends PoEnvelopeException {

    public TotalEnvelopeSizeExceededException(String message) {
        super("TOTAL_ENVELOPE_SIZE_EXCEEDED", message, HttpStatus.BAD_REQUEST);
    }
}
