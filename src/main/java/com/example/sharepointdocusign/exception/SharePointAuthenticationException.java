package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when the Microsoft Entra client-credentials token request fails.
 */
public class SharePointAuthenticationException extends PoEnvelopeException {

    public SharePointAuthenticationException(String message) {
        super("SHAREPOINT_AUTHENTICATION_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    public SharePointAuthenticationException(String message, Throwable cause) {
        super("SHAREPOINT_AUTHENTICATION_FAILED", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
