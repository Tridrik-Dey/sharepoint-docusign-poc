package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a document (main PO upload or a SharePoint file) fails PDF
 * validation - wrong MIME type, missing %PDF magic bytes, or empty content.
 */
public class UnsupportedDocumentException extends PoEnvelopeException {

    public UnsupportedDocumentException(String message) {
        super("INVALID_PDF", message, HttpStatus.BAD_REQUEST);
    }
}
