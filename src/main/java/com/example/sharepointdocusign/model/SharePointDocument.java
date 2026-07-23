package com.example.sharepointdocusign.model;

/**
 * A single PDF document retrieved from (or, in mock mode, simulated from)
 * SharePoint. Content is never logged - only fileName, size and sha256.
 */
public record SharePointDocument(
        String itemId,
        String fileName,
        String contentType,
        long size,
        byte[] content,
        String sha256) {
}
