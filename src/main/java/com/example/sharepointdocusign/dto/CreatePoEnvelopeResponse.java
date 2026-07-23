package com.example.sharepointdocusign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Single response shape for POST /api/v1/po-envelopes, covering both success
 * and failure. Fields that do not apply to the outcome are omitted from the
 * JSON payload (NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreatePoEnvelopeResponse(
        boolean success,
        String poNumber,
        String revision,
        String envelopeId,
        String status,
        List<String> documentsIncluded,
        List<String> warnings,
        String errorCode,
        String message,
        String correlationId) {

    public static CreatePoEnvelopeResponse success(
            String poNumber,
            String revision,
            String envelopeId,
            String status,
            List<String> documentsIncluded,
            List<String> warnings,
            String correlationId) {
        return new CreatePoEnvelopeResponse(
                true, poNumber, revision, envelopeId, status, documentsIncluded, warnings, null, null, correlationId);
    }

    public static CreatePoEnvelopeResponse failure(
            String poNumber,
            String revision,
            String errorCode,
            String message,
            String correlationId) {
        return new CreatePoEnvelopeResponse(
                false, poNumber, revision, null, null, null, null, errorCode, message, correlationId);
    }
}
