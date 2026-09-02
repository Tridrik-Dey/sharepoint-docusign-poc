package com.example.sharepointdocusign.controller;

import com.example.sharepointdocusign.config.DocusignProperties;
import com.example.sharepointdocusign.exception.DocusignWebhookAuthenticationException;
import com.example.sharepointdocusign.exception.InvalidRequestException;
import com.example.sharepointdocusign.service.DocusignEnvelopeCompletionService;
import com.example.sharepointdocusign.util.HmacSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Receives DocuSign Connect's "envelope completed" webhook notification.
 * Deliberately mounted OUTSIDE /api/** so ApiKeyFilter never touches it -
 * DocuSign can't send an X-Api-Key header. Authenticity is instead verified
 * via the X-DocuSign-Signature-1 HMAC header (mandatory, fails closed if
 * unconfigured), matching DocuSign Connect's own signing mechanism.
 *
 * Only active where real DocuSign envelopes actually exist (same profile
 * guard as DocusignClient/DocusignEnvelopeServiceImpl) - under mock or
 * sharepoint-test this bean/path simply doesn't exist, since no real
 * envelope could ever trigger a real Connect callback there. (Requesting the
 * path there still returns an HTTP response, not a connection failure - see
 * GlobalExceptionHandler's catch-all - but no webhook logic ever runs.)
 *
 * Nothing about the webhook payload's shape or content is trusted beyond the
 * envelope id - the actual status and SAP_PO_NUMBER/SAP_PO_REVISION custom
 * fields are re-fetched from DocuSign's own API by DocusignEnvelopeCompletionService.
 */
@RestController
@Profile("!mock & !sharepoint-test")
public class DocusignWebhookController {

    private static final Logger log = LoggerFactory.getLogger(DocusignWebhookController.class);
    public static final String SIGNATURE_HEADER = "X-DocuSign-Signature-1";

    private final DocusignEnvelopeCompletionService completionService;
    private final ObjectMapper objectMapper;
    private final DocusignProperties properties;

    public DocusignWebhookController(
            DocusignEnvelopeCompletionService completionService, ObjectMapper objectMapper, DocusignProperties properties) {
        this.completionService = completionService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostMapping("/webhooks/docusign/envelope-completed")
    public ResponseEntity<Void> handleConnectNotification(HttpServletRequest request) throws IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();

        if (!HmacSignatureVerifier.isValid(rawBody, properties.connectHmacSecret(), request.getHeader(SIGNATURE_HEADER))) {
            throw new DocusignWebhookAuthenticationException("DocuSign Connect webhook signature verification failed.");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new InvalidRequestException("DocuSign Connect webhook body is not valid JSON.");
        }

        String envelopeId = extractEnvelopeId(payload);
        if (envelopeId == null || envelopeId.isBlank()) {
            log.warn("DocuSign Connect webhook payload had no extractable envelopeId - ignoring");
            return ResponseEntity.ok().build();
        }

        completionService.processEnvelopeCompletion(envelopeId);
        return ResponseEntity.ok().build();
    }

    /** Tolerates both the nested "data.envelopeId" (v2.1 aggregate) and a flat "envelopeId" payload shape. */
    private String extractEnvelopeId(JsonNode payload) {
        String nested = payload.path("data").path("envelopeId").asText(null);
        if (nested != null && !nested.isBlank()) {
            return nested;
        }
        return payload.path("envelopeId").asText(null);
    }
}
