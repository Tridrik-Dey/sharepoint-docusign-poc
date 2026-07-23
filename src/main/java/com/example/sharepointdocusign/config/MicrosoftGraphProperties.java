package com.example.sharepointdocusign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "microsoft-graph")
public record MicrosoftGraphProperties(
        String tenantId,
        String clientId,
        String clientSecret,
        String sharepointHostname,
        String sharepointSitePath,
        String sharepointDriveId,
        String graphBaseUrl,
        String tokenUrl,
        String documentsRootFolder,
        long connectTimeoutMs,
        long readTimeoutMs,
        long responseTimeoutMs) {
}
