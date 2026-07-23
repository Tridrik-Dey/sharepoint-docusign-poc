package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.MicrosoftGraphProperties;
import com.example.sharepointdocusign.exception.SharePointAuthenticationException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Obtains and caches a Microsoft Graph access token via the OAuth 2.0
 * client-credentials grant. The token is cached until shortly before
 * expiration and transparently refreshed - never logged.
 */
@Service
@Profile("!mock")
public class MicrosoftTokenService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftTokenService.class);
    private static final String GRAPH_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";
    private static final long EXPIRY_BUFFER_SECONDS = 60;

    private final WebClient microsoftIdentityWebClient;
    private final MicrosoftGraphProperties properties;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public MicrosoftTokenService(WebClient microsoftIdentityWebClient, MicrosoftGraphProperties properties) {
        this.microsoftIdentityWebClient = microsoftIdentityWebClient;
        this.properties = properties;
    }

    public String getAccessToken() {
        if (isValid()) {
            return cachedToken;
        }
        lock.lock();
        try {
            if (isValid()) {
                return cachedToken;
            }
            return requestNewToken();
        } finally {
            lock.unlock();
        }
    }

    private boolean isValid() {
        return cachedToken != null && Instant.now().isBefore(cachedTokenExpiry.minusSeconds(EXPIRY_BUFFER_SECONDS));
    }

    private String requestNewToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", GRAPH_DEFAULT_SCOPE);
        form.add("grant_type", "client_credentials");

        try {
            TokenResponse response = microsoftIdentityWebClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new SharePointAuthenticationException("Microsoft identity platform returned an empty token response.");
            }

            cachedToken = response.accessToken();
            cachedTokenExpiry = Instant.now().plusSeconds(response.expiresIn());
            log.info("Obtained new Microsoft Graph access token, expires in {}s", response.expiresIn());
            return cachedToken;
        } catch (WebClientResponseException e) {
            log.error("Microsoft identity token request failed with HTTP status {}", e.getStatusCode().value());
            throw new SharePointAuthenticationException("Failed to authenticate with Microsoft Entra ID.", e);
        } catch (SharePointAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while requesting a Microsoft Graph access token", e);
            throw new SharePointAuthenticationException("Failed to authenticate with Microsoft Entra ID.", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
