package com.example.sharepointdocusign.model;

import java.util.List;

/**
 * Outcome of creating (and, when enabled, sending) a DocuSign envelope.
 */
public record EnvelopeCreationResult(
        String envelopeId,
        String status,
        List<String> documentsIncluded,
        List<String> warnings) {
}
