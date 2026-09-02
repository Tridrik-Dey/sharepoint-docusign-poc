package com.example.sharepointdocusign.model;

import java.util.Map;

/**
 * Authoritative envelope status + custom fields, fetched directly from
 * DocuSign's own API rather than trusted from a webhook payload.
 */
public record EnvelopeStatusResult(String envelopeId, String status, Map<String, String> customFields) {

    public String customField(String name) {
        return customFields.get(name);
    }
}
