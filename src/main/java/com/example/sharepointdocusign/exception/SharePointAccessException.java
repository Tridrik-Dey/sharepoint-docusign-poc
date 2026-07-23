package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when Microsoft Graph denies access to the site/drive/folder (HTTP 403).
 */
public class SharePointAccessException extends PoEnvelopeException {

    public SharePointAccessException(String message) {
        super("SHAREPOINT_ACCESS_DENIED", message, HttpStatus.BAD_GATEWAY);
    }

    public SharePointAccessException(String message, Throwable cause) {
        super("SHAREPOINT_ACCESS_DENIED", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
