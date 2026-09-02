package com.example.sharepointdocusign.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the whole DocuSign-completed -> SharePoint chain end to end: a
 * realistic Connect webhook POST, correctly HMAC-signed, triggers this app
 * to call DocuSign's own API for the authoritative envelope status + custom
 * fields, download the combined signed document, and upload it to
 * SharePoint - with DocuSign and Microsoft Graph both stood in by a single
 * WireMock server. Mirrors PoEnvelopeRealProfileIntegrationTest's structure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
class DocusignWebhookRealProfileIntegrationTest {

    private static final String SECRET = "test-webhook-secret";
    private static WireMockServer wireMockServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void registerWireMockUrls(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        String baseUrl = "http://localhost:" + wireMockServer.port();
        registry.add("microsoft-graph.graph-base-url", () -> baseUrl);
        registry.add("microsoft-graph.token-url", () -> baseUrl + "/test-tenant/oauth2/v2.0/token");
        registry.add("docusign.base-path", () -> baseUrl + "/restapi");
        registry.add("docusign.oauth-base-path", () -> baseUrl);
    }

    @Test
    void webhookNotificationStoresTheSignedDocumentInSharePoint() throws Exception {
        stubFor(post(urlPathEqualTo("/test-tenant/oauth2/v2.0/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-graph-token","token_type":"Bearer","expires_in":3600}
                        """)));

        stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-docusign-token","token_type":"Bearer","expires_in":3600}
                        """)));

        stubFor(get(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes/env-e2e-1?include=custom_fields"))
                .willReturn(okJson("""
                        {
                          "envelopeId": "env-e2e-1",
                          "status": "completed",
                          "customFields": {
                            "textCustomFields": [
                              {"name": "SAP_PO_NUMBER", "value": "4500000105", "show": "false"},
                              {"name": "SAP_PO_REVISION", "value": "02", "show": "false"}
                            ]
                          }
                        }
                        """)));

        byte[] signedPdf = "%PDF-1.4\nsigned-by-vendor\n%%EOF".getBytes();
        stubFor(get(urlEqualTo("/restapi/v2.1/accounts/test-account-id/envelopes/env-e2e-1/documents/combined"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(signedPdf)));

        stubFor(put(urlEqualTo(
                "/v1.0/drives/test-drive-id/root:/4500000105/REV-02/Signed-PO-4500000105-REV-02.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-signed\",\"name\":\"Signed-PO-4500000105-REV-02.pdf\",\"size\":" + signedPdf.length + "}")));

        String body = """
                {"event":"envelope-completed","data":{"envelopeId":"env-e2e-1"}}""";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-DocuSign-Signature-1", sign(body, SECRET));
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/webhooks/docusign/envelope-completed", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(putRequestedFor(urlEqualTo(
                "/v1.0/drives/test-drive-id/root:/4500000105/REV-02/Signed-PO-4500000105-REV-02.pdf:/content?@microsoft.graph.conflictBehavior=rename")));
    }

    private String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
