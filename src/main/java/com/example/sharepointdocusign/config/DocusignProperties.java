package com.example.sharepointdocusign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docusign")
public record DocusignProperties(
        String integrationKey,
        String userId,
        String accountId,
        String privateKeyPath,
        String basePath,
        String oauthBasePath,
        long tokenExpiryBufferSeconds,
        long connectTimeoutMs,
        long responseTimeoutMs) {
}
