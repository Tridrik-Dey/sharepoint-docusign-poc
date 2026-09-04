package com.example.sharepointdocusign.wiremock;

import com.example.sharepointdocusign.client.DocusignClient;
import com.example.sharepointdocusign.client.DocusignClient.CustomFields;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeDefinitionRequest;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeDocumentPayload;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeResponse;
import com.example.sharepointdocusign.client.DocusignClient.Recipients;
import com.example.sharepointdocusign.client.DocusignClient.SignHereTab;
import com.example.sharepointdocusign.client.DocusignClient.Signer;
import com.example.sharepointdocusign.client.DocusignClient.Tabs;
import com.example.sharepointdocusign.config.DocusignProperties;
import com.example.sharepointdocusign.exception.DocusignAuthenticationException;
import com.example.sharepointdocusign.exception.DocusignEnvelopeException;
import com.example.sharepointdocusign.model.EnvelopeStatusResult;
import com.example.sharepointdocusign.service.DocusignAuthService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises DocusignClient's envelope creation call against a WireMock
 * stand-in for the DocuSign eSignature REST API - both the success and
 * failure (no envelope id returned to the caller) paths.
 */
class DocusignClientWireMockTest {

    private WireMockServer wireMockServer;
    private DocusignClient docusignClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        DocusignProperties properties = new DocusignProperties(
                "test-integration-key", "test-user-id", "test-account-id",
                "classpath:test-private-key.pem",
                "http://localhost:" + wireMockServer.port() + "/restapi",
                "localhost:" + wireMockServer.port(),
                300, 5000, 15000, "test-webhook-secret");

        DocusignAuthService authService = mock(DocusignAuthService.class);
        when(authService.getAccessToken()).thenReturn("fake-docusign-token");

        WebClient docusignWebClient = WebClient.builder()
                .baseUrl(properties.basePath())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .responseTimeout(Duration.ofSeconds(5))))
                .build();

        docusignClient = new DocusignClient(docusignWebClient, authService, properties);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private EnvelopeDefinitionRequest sampleEnvelopeDefinition(String status) {
        return new EnvelopeDefinitionRequest(
                "PO 4500000105 Revision 02 - Signature Request",
                "Please review and sign Purchase Order 4500000105, revision 02.",
                List.of(new EnvelopeDocumentPayload("1", "Purchase-Order-4500000105.pdf", "pdf", "JVBERi0xLjQK")),
                new Recipients(List.of(new Signer(
                        "test@example.com", "Dummy Vendor SRL", "1", "1",
                        new Tabs(List.of(new SignHereTab("1", "/vendor-signature/", "pixels", "0", "-10", false)))))),
                new CustomFields(List.of()),
                status);
    }

    @Test
    void createsAndSendsEnvelopeSuccessfully() {
        stubFor(post(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes"))
                .withRequestBody(matchingJsonPath("$.status", WireMock.equalTo("sent")))
                .willReturn(okJson("""
                        {"envelopeId":"envelope-abc-123","status":"sent","uri":"/envelopes/envelope-abc-123"}
                        """)));

        EnvelopeResponse response = docusignClient.createEnvelope(sampleEnvelopeDefinition("sent"));

        assertThat(response.envelopeId()).isEqualTo("envelope-abc-123");
        assertThat(response.status()).isEqualTo("sent");
        verify(postRequestedFor(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes")));
    }

    @Test
    void createsDraftEnvelopeWhenStatusIsCreated() {
        stubFor(post(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes"))
                .withRequestBody(matchingJsonPath("$.status", WireMock.equalTo("created")))
                .willReturn(okJson("""
                        {"envelopeId":"envelope-draft-456","status":"created"}
                        """)));

        EnvelopeResponse response = docusignClient.createEnvelope(sampleEnvelopeDefinition("created"));

        assertThat(response.envelopeId()).isEqualTo("envelope-draft-456");
        assertThat(response.status()).isEqualTo("created");
    }

    @Test
    void envelopeCreationFailureThrowsWithoutReturningAnEnvelopeId() {
        stubFor(post(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errorCode\":\"INVALID_EMAIL_ADDRESS\",\"message\":\"...\"}")));

        assertThatThrownBy(() -> docusignClient.createEnvelope(sampleEnvelopeDefinition("sent")))
                .isInstanceOf(DocusignEnvelopeException.class);
    }

    @Test
    void fetchesEnvelopeStatusAndCustomFields() {
        stubFor(get(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes/envelope-abc-123?include=custom_fields"))
                .willReturn(okJson("""
                        {
                          "envelopeId": "envelope-abc-123",
                          "status": "completed",
                          "customFields": {
                            "textCustomFields": [
                              {"name": "SAP_PO_NUMBER", "value": "4500000105", "show": "false"},
                              {"name": "SAP_PO_REVISION", "value": "02", "show": "false"},
                              {"name": "SHAREPOINT_FOLDER_PATH", "value": "4500000105/02", "show": "false"}
                            ]
                          }
                        }
                        """)));

        EnvelopeStatusResult result = docusignClient.getEnvelopeWithCustomFields("envelope-abc-123");

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.customField("SAP_PO_NUMBER")).isEqualTo("4500000105");
        assertThat(result.customField("SAP_PO_REVISION")).isEqualTo("02");
    }

    @Test
    void mapsEnvelopeStatus401ToAuthenticationFailedException() {
        stubFor(get(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes/envelope-abc-123?include=custom_fields"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> docusignClient.getEnvelopeWithCustomFields("envelope-abc-123"))
                .isInstanceOf(DocusignAuthenticationException.class);
    }

    @Test
    void downloadsCombinedDocument() {
        byte[] pdfBytes = "%PDF-1.4\ncombined\n%%EOF".getBytes();
        stubFor(get(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes/envelope-abc-123/documents/combined"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfBytes)));

        byte[] downloaded = docusignClient.downloadCombinedDocument("envelope-abc-123");

        assertThat(downloaded).isEqualTo(pdfBytes);
    }

    @Test
    void mapsCombinedDocumentServerErrorToEnvelopeException() {
        stubFor(get(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes/envelope-abc-123/documents/combined"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> docusignClient.downloadCombinedDocument("envelope-abc-123"))
                .isInstanceOf(DocusignEnvelopeException.class);
    }
}
