package com.example.sharepointdocusign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Single response shape for POST /api/v1/po-documents/{poNumber}/{revision}
 * (and the flat-folder variant), covering both success and failure. Stores
 * one document into the matching SharePoint folder - no DocuSign or envelope
 * logic is involved. {@code fileName} on success is the name the document
 * was actually stored under, which may differ from the uploaded name if
 * SharePoint auto-renamed it to avoid overwriting an existing file - see
 * {@code renamed}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PoDocumentUploadResponse(
        boolean success,
        String poNumber,
        String revision,
        String fileName,
        Long size,
        String sha256,
        Boolean renamed,
        String errorCode,
        String message,
        String correlationId) {

    public static PoDocumentUploadResponse success(
            String poNumber, String revision, String fileName, long size, String sha256, boolean renamed,
            String correlationId) {
        return new PoDocumentUploadResponse(
                true, poNumber, revision, fileName, size, sha256, renamed, null, null, correlationId);
    }

    public static PoDocumentUploadResponse failure(
            String poNumber, String revision, String errorCode, String message, String correlationId) {
        return new PoDocumentUploadResponse(
                false, poNumber, revision, null, null, null, null, errorCode, message, correlationId);
    }
}
