package com.example.sharepointdocusign.wiremock;

import com.example.sharepointdocusign.config.DocusignProperties;
import com.example.sharepointdocusign.exception.DocusignAuthenticationException;
import com.example.sharepointdocusign.service.DocusignAuthService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises DocusignAuthService's JWT-grant flow against a WireMock stand-in
 * for the DocuSign OAuth token endpoint, using a locally generated (test
 * only, never a real DocuSign key) RSA private key fixture.
 */
class DocusignAuthServiceWireMockTest {

    private WireMockServer wireMockServer;
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .responseTimeout(Duration.ofSeconds(5))))
                .build();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private DocusignProperties propertiesWithKey(String keyClasspathFile) {
        return new DocusignProperties(
                "test-integration-key", "test-user-id", "test-account-id",
                "classpath:" + keyClasspathFile,
                "http://localhost:" + wireMockServer.port() + "/restapi",
                "localhost:" + wireMockServer.port(),
                300, 5000, 15000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-private-key.pem", "test-private-key-pkcs1.pem"})
    void obtainsAccessTokenUsingJwtGrant(String keyFile) {
        stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-docusign-token","token_type":"Bearer","expires_in":3600}
                        """)));

        DocusignAuthService authService = new DocusignAuthService(webClient, propertiesWithKey(keyFile));

        String token = authService.getAccessToken();

        assertThat(token).isEqualTo("fake-docusign-token");
        verify(postRequestedFor(urlPathEqualTo("/oauth/token"))
                .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"))
                .withRequestBody(containing("assertion=")));
    }

    @Test
    void cachesTokenAndDoesNotRequestTwice() {
        stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-docusign-token","token_type":"Bearer","expires_in":3600}
                        """)));

        DocusignAuthService authService = new DocusignAuthService(webClient, propertiesWithKey("test-private-key.pem"));

        authService.getAccessToken();
        authService.getAccessToken();

        verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    void wrapsAuthenticationFailureIntoDomainException() {
        stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"invalid_grant\"}")));

        DocusignAuthService authService = new DocusignAuthService(webClient, propertiesWithKey("test-private-key.pem"));

        assertThatThrownBy(authService::getAccessToken).isInstanceOf(DocusignAuthenticationException.class);
    }

    @Test
    void wrapsMissingPrivateKeyFileIntoDomainException() {
        DocusignAuthService authService = new DocusignAuthService(webClient, propertiesWithKey("does-not-exist.pem"));

        assertThatThrownBy(authService::getAccessToken).isInstanceOf(DocusignAuthenticationException.class);
    }
}
