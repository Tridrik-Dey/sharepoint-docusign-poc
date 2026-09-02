package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when uploading a document to SharePoint fails for a reason not
 * covered by a more specific exception (authentication, access denial or
 * folder-not-found are mapped separately - see MicrosoftGraphClient).
 */
public class SharePointUploadFailedException extends PoEnvelopeException {

    public SharePointUploadFailedException(String message) {
        super("SHAREPOINT_UPLOAD_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    public SharePointUploadFailedException(String message, Throwable cause) {
        super("SHAREPOINT_UPLOAD_FAILED", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
