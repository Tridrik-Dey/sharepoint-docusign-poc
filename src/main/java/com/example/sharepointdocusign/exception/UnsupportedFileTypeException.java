package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a document uploaded to the SAP write-back endpoint
 * (POST /api/v1/po-documents/...) has a file type outside the allowed set
 * (see FileValidationUtil.SUPPORTED_UPLOAD_EXTENSIONS). Kept distinct from
 * UnsupportedDocumentException/INVALID_PDF, which remains specifically for
 * PDF-shaped content that fails PDF validation - this endpoint accepts more
 * than PDFs, so "not a PDF" is no longer itself an error here.
 */
public class UnsupportedFileTypeException extends PoEnvelopeException {

    public UnsupportedFileTypeException(String message) {
        super("UNSUPPORTED_FILE_TYPE", message, HttpStatus.BAD_REQUEST);
    }
}
