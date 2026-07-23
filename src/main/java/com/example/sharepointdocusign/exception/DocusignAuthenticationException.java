package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when the DocuSign JWT grant / user-token exchange fails.
 */
public class DocusignAuthenticationException extends PoEnvelopeException {

    public DocusignAuthenticationException(String message) {
        super("DOCUSIGN_AUTHENTICATION_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    public DocusignAuthenticationException(String message, Throwable cause) {
        super("DOCUSIGN_AUTHENTICATION_FAILED", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
