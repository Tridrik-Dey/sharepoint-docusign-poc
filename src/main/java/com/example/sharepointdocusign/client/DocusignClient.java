package com.example.sharepointdocusign.client;

import com.example.sharepointdocusign.config.DocusignProperties;
import com.example.sharepointdocusign.exception.DocusignAuthenticationException;
import com.example.sharepointdocusign.exception.DocusignEnvelopeException;
import com.example.sharepointdocusign.model.EnvelopeStatusResult;
import com.example.sharepointdocusign.service.DocusignAuthService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            throw mapDocusignError(e, "create the DocuSign envelope");
        } catch (DocusignEnvelopeException | DocusignAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while creating the DocuSign envelope", e);
            throw new DocusignEnvelopeException("Unexpected error while creating the DocuSign envelope.", e);
        }
    }

    /** Fetches the envelope's current status and custom fields (SAP_PO_NUMBER, SAP_PO_REVISION, ...). */
    public EnvelopeStatusResult getEnvelopeWithCustomFields(String envelopeId) {
        try {
            EnvelopeDetailResponse response = docusignWebClient.get()
                    .uri("/v2.1/accounts/{accountId}/envelopes/{envelopeId}?include=custom_fields",
                            properties.accountId(), envelopeId)
                    .headers(headers -> headers.setBearerAuth(authService.getAccessToken()))
                    .retrieve()
                    .bodyToMono(EnvelopeDetailResponse.class)
                    .block();

            if (response == null || response.status() == null) {
                throw new DocusignEnvelopeException("DocuSign returned an empty envelope status response.");
            }
            Map<String, String> customFields = new HashMap<>();
            if (response.customFields() != null && response.customFields().textCustomFields() != null) {
                for (TextCustomField field : response.customFields().textCustomFields()) {
                    customFields.put(field.name(), field.value());
                }
            }
            return new EnvelopeStatusResult(envelopeId, response.status(), customFields);
        } catch (WebClientResponseException e) {
            throw mapDocusignError(e, "fetch status for DocuSign envelope " + envelopeId);
        } catch (DocusignEnvelopeException | DocusignAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching DocuSign envelope status", e);
            throw new DocusignEnvelopeException("Unexpected error while fetching the DocuSign envelope status.", e);
        }
    }

    /** Downloads every document in the envelope merged into one PDF, including DocuSign's Certificate of Completion. */
    public byte[] downloadCombinedDocument(String envelopeId) {
        try {
            byte[] content = docusignWebClient.get()
                    .uri("/v2.1/accounts/{accountId}/envelopes/{envelopeId}/documents/combined",
                            properties.accountId(), envelopeId)
                    .headers(headers -> headers.setBearerAuth(authService.getAccessToken()))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (content == null || content.length == 0) {
                throw new DocusignEnvelopeException("DocuSign returned an empty combined document for envelope " + envelopeId + ".");
            }
            return content;
        } catch (WebClientResponseException e) {
            throw mapDocusignError(e, "download the combined document for DocuSign envelope " + envelopeId);
        } catch (DocusignEnvelopeException | DocusignAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while downloading the DocuSign combined document", e);
            throw new DocusignEnvelopeException("Unexpected error while downloading the DocuSign combined document.", e);
        }
    }

    private RuntimeException mapDocusignError(WebClientResponseException e, String action) {
        log.warn("DocuSign call failed while trying to {} (HTTP {})", action, e.getStatusCode().value());
        if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            return new DocusignAuthenticationException("DocuSign rejected the request credentials while trying to " + action + ".", e);
        }
        return new DocusignEnvelopeException(
                "DocuSign request failed while trying to " + action + " (HTTP " + e.getStatusCode().value() + ").", e);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvelopeDetailResponse(String envelopeId, String status, ResponseCustomFields customFields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseCustomFields(List<TextCustomField> textCustomFields) {
    }
}
