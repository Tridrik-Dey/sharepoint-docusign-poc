package com.example.sharepointdocusign.client;

import com.example.sharepointdocusign.config.DocusignProperties;
import com.example.sharepointdocusign.exception.DocusignAuthenticationException;
import com.example.sharepointdocusign.exception.DocusignEnvelopeException;
import com.example.sharepointdocusign.service.DocusignAuthService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

/**
 * Thin wrapper around the DocuSign eSignature REST API envelope creation
 * endpoint. Envelope business logic (document order, tabs, custom fields)
 * lives in DocusignEnvelopeServiceImpl - this class only sends the request
 * and maps DocuSign HTTP errors to domain exceptions, never forwarding raw
 * DocuSign response bodies to API callers. Inactive on "sharepoint-test" as
 * well as "mock", since that profile never creates real DocuSign envelopes.
 */
@Component
@Profile("!mock & !sharepoint-test")
public class DocusignClient {

    private static final Logger log = LoggerFactory.getLogger(DocusignClient.class);

    private final WebClient docusignWebClient;
    private final DocusignAuthService authService;
    private final DocusignProperties properties;

    public DocusignClient(WebClient docusignWebClient, DocusignAuthService authService, DocusignProperties properties) {
        this.docusignWebClient = docusignWebClient;
        this.authService = authService;
        this.properties = properties;
    }

    public EnvelopeResponse createEnvelope(EnvelopeDefinitionRequest envelopeDefinition) {
        try {
            EnvelopeResponse response = docusignWebClient.post()
                    .uri("/v2.1/accounts/{accountId}/envelopes", properties.accountId())
                    .headers(headers -> headers.setBearerAuth(authService.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(envelopeDefinition)
                    .retrieve()
                    .bodyToMono(EnvelopeResponse.class)
                    .block();

            if (response == null || response.envelopeId() == null || response.envelopeId().isBlank()) {
                throw new DocusignEnvelopeException("DocuSign returned an empty envelope response.");
            }
            return response;
        } catch (WebClientResponseException e) {
            log.warn("DocuSign envelope creation failed with HTTP status {}", e.getStatusCode().value());
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new DocusignAuthenticationException("DocuSign rejected the request credentials.", e);
            }
            throw new DocusignEnvelopeException(
                    "DocuSign rejected the envelope (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (DocusignEnvelopeException | DocusignAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while creating the DocuSign envelope", e);
            throw new DocusignEnvelopeException("Unexpected error while creating the DocuSign envelope.", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvelopeDefinitionRequest(
            String emailSubject,
            String emailBlurb,
            List<EnvelopeDocumentPayload> documents,
            Recipients recipients,
            CustomFields customFields,
            String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvelopeDocumentPayload(String documentId, String name, String fileExtension, String documentBase64) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recipients(List<Signer> signers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Signer(String email, String name, String recipientId, String routingOrder, Tabs tabs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tabs(List<SignHereTab> signHereTabs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignHereTab(
            String documentId,
            String anchorString,
            String anchorUnits,
            String anchorXOffset,
            String anchorYOffset,
            Boolean anchorCaseSensitive) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomFields(List<TextCustomField> textCustomFields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextCustomField(String name, String value, String show) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvelopeResponse(String envelopeId, String status, String uri, String statusDateTime) {
    }
}
