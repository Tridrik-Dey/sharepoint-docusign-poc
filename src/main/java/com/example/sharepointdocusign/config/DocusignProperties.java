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
        long responseTimeoutMs,
        /**
         * Shared secret configured in DocuSign Connect's admin console, used to
         * verify the X-DocuSign-Signature-1 HMAC-SHA256 header on inbound
         * webhook notifications. Unlike app.security.api-key, a blank value
         * here does NOT disable protection - it means the webhook rejects
         * every request (fail closed), since this endpoint triggers a real
         * SharePoint write and has no other authentication of its own.
         */
        String connectHmacSecret) {
}
