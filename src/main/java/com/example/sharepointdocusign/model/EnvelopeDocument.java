package com.example.sharepointdocusign.model;

/**
 * A document ready to be embedded in a DocuSign envelope, in final send order.
 * documentId values are assigned sequentially starting at 1, main document first.
 */
public record EnvelopeDocument(
        int documentId,
        String name,
        byte[] content,
        String contentType,
        boolean mainDocument) {
}
