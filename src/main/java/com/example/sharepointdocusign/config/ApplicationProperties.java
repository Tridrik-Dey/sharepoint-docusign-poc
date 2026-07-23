package com.example.sharepointdocusign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(Docusign docusign, Documents documents, Mock mock) {

    public record Docusign(boolean sendEnvelope) {
    }

    public record Documents(
            int maxMainDocumentSizeMb,
            int maxSharepointDocumentSizeMb,
            int maxTotalEnvelopeSizeMb,
            int maxSupportingDocuments) {

        public long maxMainDocumentSizeBytes() {
            return maxMainDocumentSizeMb * 1024L * 1024L;
        }

        public long maxSharepointDocumentSizeBytes() {
            return maxSharepointDocumentSizeMb * 1024L * 1024L;
        }

        public long maxTotalEnvelopeSizeBytes() {
            return maxTotalEnvelopeSizeMb * 1024L * 1024L;
        }
    }

    public record Mock(String sharepointRoot) {
    }
}
