package com.example.sharepointdocusign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Single response shape for GET /api/v1/po-documents/{poNumber}/{revision},
 * covering both success and failure. No DocuSign or envelope logic is
 * involved here - this endpoint only retrieves and returns the matching
 * SharePoint documents, for callers (e.g. SAP) that want to handle the
 * document themselves rather than have it placed into a DocuSign envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PoDocumentsResponse(
        boolean success,
        String poNumber,
        String revision,
        List<DocumentPayload> documents,
        String errorCode,
        String message,
        String correlationId) {

    public static PoDocumentsResponse success(
            String poNumber, String revision, List<DocumentPayload> documents, String correlationId) {
        return new PoDocumentsResponse(true, poNumber, revision, documents, null, null, correlationId);
    }

    public static PoDocumentsResponse failure(
            String poNumber, String revision, String errorCode, String message, String correlationId) {
        return new PoDocumentsResponse(false, poNumber, revision, null, errorCode, message, correlationId);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DocumentPayload(
            String fileName,
            String contentType,
            long size,
            String sha256,
            String contentBase64) {
    }
}
