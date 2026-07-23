package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when DocuSign rejects envelope creation/sending. No envelope id
 * must ever be returned to the caller alongside this failure.
 */
public class DocusignEnvelopeException extends PoEnvelopeException {

    public DocusignEnvelopeException(String message) {
        super("DOCUSIGN_ENVELOPE_CREATION_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    public DocusignEnvelopeException(String message, Throwable cause) {
        super("DOCUSIGN_ENVELOPE_CREATION_FAILED", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
