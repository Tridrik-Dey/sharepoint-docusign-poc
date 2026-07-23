package com.example.sharepointdocusign.controller;

import com.example.sharepointdocusign.dto.CreatePoEnvelopeResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the "real" (non-mock) wiring: the controller,
 * orchestration, real MicrosoftGraphClient/SharePointDocumentServiceImpl and
 * real DocusignClient/DocusignEnvelopeServiceImpl all cooperating, with
 * Microsoft Graph and DocuSign both stood in by a single WireMock server.
 * This is the only test that proves Phase 2 and Phase 3 wiring works
 * together through the full Spring context, not just in isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
class PoEnvelopeRealProfileIntegrationTest {

    private static WireMockServer wireMockServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    // @DynamicPropertySource callbacks run during context preparation, before any
    // @BeforeAll method on this class - so WireMock is started here, exactly once,
    // rather than in a separate @BeforeAll (which would race and start a second,
    // differently-ported server after the properties above had already been fixed).
    @DynamicPropertySource
    static void registerWireMockUrls(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        String baseUrl = "http://localhost:" + wireMockServer.port();
        registry.add("microsoft-graph.graph-base-url", () -> baseUrl);
        registry.add("microsoft-graph.token-url", () -> baseUrl + "/test-tenant/oauth2/v2.0/token");
        registry.add("docusign.base-path", () -> baseUrl + "/restapi");
        // WebClientConfig forces "https://" onto any oauth-base-path lacking a scheme (matching
        // real DocuSign config like "account-d.docusign.com") - pass an explicit http:// scheme
        // here since WireMock only serves plain HTTP.
        registry.add("docusign.oauth-base-path", () -> baseUrl);
    }

    @Test
    void fullRealFlowCreatesAndSendsEnvelope() {
        stubFor(post(urlPathEqualTo("/test-tenant/oauth2/v2.0/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-graph-token","token_type":"Bearer","expires_in":3600}
                        """)));

        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/REV-02:/children"))
                .willReturn(okJson("""
                        {
                          "value": [
                            {"id": "item-1", "name": "Technical-Specification.pdf", "file": {"mimeType": "application/pdf"}}
                          ]
                        }
                        """)));

        byte[] sharePointPdf = "%PDF-1.4\n%%EOF".getBytes();
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/item-1/content"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(sharePointPdf)));

        stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-docusign-token","token_type":"Bearer","expires_in":3600}
                        """)));

        stubFor(post(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes"))
                .willReturn(okJson("""
                        {"envelopeId":"envelope-real-flow-1","status":"sent"}
                        """)));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders metadataPartHeaders = new HttpHeaders();
        metadataPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        form.add("metadata", new HttpEntity<>("""
                {"poNumber":"4500000105","revision":"02","vendorName":"Dummy Vendor SRL","vendorEmail":"test@example.com"}
                """, metadataPartHeaders));

        HttpHeaders filePartHeaders = new HttpHeaders();
        filePartHeaders.setContentType(MediaType.APPLICATION_PDF);
        ByteArrayResource fileResource = new ByteArrayResource("%PDF-1.4\nsample\n%%EOF".getBytes()) {
            @Override
            public String getFilename() {
                return "Purchase-Order-4500000105.pdf";
            }
        };
        form.add("purchaseOrderDocument", new HttpEntity<>(fileResource, filePartHeaders));

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(form, requestHeaders);

        ResponseEntity<CreatePoEnvelopeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/po-envelopes", request, CreatePoEnvelopeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CreatePoEnvelopeResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isTrue();
        assertThat(body.envelopeId()).isEqualTo("envelope-real-flow-1");
        assertThat(body.status()).isEqualTo("sent");
        assertThat(body.documentsIncluded()).containsExactly(
                "Purchase-Order-4500000105.pdf", "Technical-Specification.pdf");
    }
}
