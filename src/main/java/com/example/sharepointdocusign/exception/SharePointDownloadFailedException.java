package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when any single SharePoint file fails to download. Per the business
 * rule, this must abort the whole request - no partial envelope is created.
 */
public class SharePointDownloadFailedException extends PoEnvelopeException {

    public SharePointDownloadFailedException(String message) {
        super("SHAREPOINT_DOWNLOAD_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    public SharePointDownloadFailedException(String message, Throwable cause) {
        super("SHAREPOINT_DOWNLOAD_FAILED", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
