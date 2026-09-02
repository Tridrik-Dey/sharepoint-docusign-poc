package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an inbound DocuSign Connect webhook notification fails HMAC
 * signature verification. Kept separate from DocusignAuthenticationException,
 * which covers this app's own outbound JWT-grant authentication to DocuSign,
 * not inbound webhook authenticity.
 */
public class DocusignWebhookAuthenticationException extends PoEnvelopeException {

    public DocusignWebhookAuthenticationException(String message) {
        super("DOCUSIGN_WEBHOOK_AUTHENTICATION_FAILED", message, HttpStatus.UNAUTHORIZED);
    }
}
