package com.example.sharepointdocusign.wiremock;

import com.example.sharepointdocusign.config.MicrosoftGraphProperties;
import com.example.sharepointdocusign.exception.SharePointAuthenticationException;
import com.example.sharepointdocusign.service.MicrosoftTokenService;
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
 * Exercises MicrosoftTokenService against a WireMock stand-in for the
 * Microsoft identity platform OAuth 2.0 client-credentials token endpoint.
 */
class MicrosoftTokenServiceWireMockTest {

    private WireMockServer wireMockServer;
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        webClient = plainWebClient();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private WebClient plainWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    private MicrosoftGraphProperties properties() {
        return new MicrosoftGraphProperties(
                "test-tenant", "test-client", "test-secret",
                "test.sharepoint.com", "/sites/Test", "test-drive-id",
                "http://localhost:" + wireMockServer.port(),
                "http://localhost:" + wireMockServer.port() + "/test-tenant/oauth2/v2.0/token",
                "PO-Documents", 5000, 10000, 15000);
    }

    @Test
    void obtainsAccessTokenFromTokenEndpoint() {
        stubFor(post(urlPathEqualTo("/test-tenant/oauth2/v2.0/token"))
                .willReturn(okJson("""
                        {"token_type":"Bearer","expires_in":3600,"access_token":"fake-access-token-123"}
                        """)));

        MicrosoftTokenService tokenService = new MicrosoftTokenService(webClient, properties());

        String token = tokenService.getAccessToken();

        assertThat(token).isEqualTo("fake-access-token-123");
        verify(postRequestedFor(urlPathEqualTo("/test-tenant/oauth2/v2.0/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("scope=https")));
    }

    @Test
    void cachesTokenAndDoesNotRequestTwice() {
        stubFor(post(urlPathEqualTo("/test-tenant/oauth2/v2.0/token"))
                .willReturn(okJson("""
                        {"token_type":"Bearer","expires_in":3600,"access_token":"fake-access-token-123"}
                        """)));

        MicrosoftTokenService tokenService = new MicrosoftTokenService(webClient, properties());

        tokenService.getAccessToken();
        tokenService.getAccessToken();

        verify(1, postRequestedFor(urlPathEqualTo("/test-tenant/oauth2/v2.0/token")));
    }

    @Test
    void wrapsAuthenticationFailureIntoDomainException() {
        stubFor(post(urlPathEqualTo("/test-tenant/oauth2/v2.0/token"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid_client\"}")));

        MicrosoftTokenService tokenService = new MicrosoftTokenService(webClient, properties());

        assertThatThrownBy(tokenService::getAccessToken)
                .isInstanceOf(SharePointAuthenticationException.class);
    }
}
