package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.DocusignProperties;
import com.example.sharepointdocusign.exception.DocusignAuthenticationException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Jwts;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Authenticates against DocuSign using the JWT grant (server-side / "service
 * integration" flow): signs a short-lived JWT assertion with the configured
 * RSA private key, exchanges it for an access token, and caches that token
 * until shortly before expiry - refreshing transparently when needed.
 * Never logs the private key, the JWT assertion or the access token itself.
 * Inactive on "sharepoint-test" - that profile uses MockDocusignEnvelopeService
 * instead, so no DocuSign private key or credentials are ever touched.
 */
@Service
@Profile("!mock & !sharepoint-test")
public class DocusignAuthService {

    private static final Logger log = LoggerFactory.getLogger(DocusignAuthService.class);
    private static final long EXPIRY_BUFFER_SECONDS = 60;
    private static final long JWT_LIFETIME_MINUTES = 60;
    private static final String JWT_SCOPE = "signature impersonation";

    private final WebClient docusignOAuthWebClient;
    private final DocusignProperties properties;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile PrivateKey cachedPrivateKey;
    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public DocusignAuthService(WebClient docusignOAuthWebClient, DocusignProperties properties) {
        this.docusignOAuthWebClient = docusignOAuthWebClient;
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
        String assertion = buildSignedJwt();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);

        try {
            TokenResponse response = docusignOAuthWebClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new DocusignAuthenticationException("DocuSign returned an empty token response.");
            }

            cachedToken = response.accessToken();
            cachedTokenExpiry = Instant.now().plusSeconds(response.expiresIn());
            log.info("Obtained new DocuSign access token, expires in {}s", response.expiresIn());
            return cachedToken;
        } catch (WebClientResponseException e) {
            log.error("DocuSign JWT token request failed with HTTP status {}", e.getStatusCode().value());
            throw new DocusignAuthenticationException("Failed to authenticate with DocuSign.", e);
        } catch (DocusignAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while requesting a DocuSign access token", e);
            throw new DocusignAuthenticationException("Failed to authenticate with DocuSign.", e);
        }
    }

    private String buildSignedJwt() {
        PrivateKey privateKey = loadPrivateKey();
        Instant now = Instant.now();
        String audience = properties.oauthBasePath().replaceFirst("^https?://", "");

        return Jwts.builder()
                .issuer(properties.integrationKey())
                .subject(properties.userId())
                .audience().add(audience).and()
                .claim("scope", JWT_SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(JWT_LIFETIME_MINUTES, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private PrivateKey loadPrivateKey() {
        if (cachedPrivateKey != null) {
            return cachedPrivateKey;
        }
        try {
            String pemText = readPemText(properties.privateKeyPath());
            cachedPrivateKey = parsePrivateKey(pemText);
            return cachedPrivateKey;
        } catch (DocusignAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load the DocuSign private key from the configured path");
            throw new DocusignAuthenticationException("Failed to load the DocuSign private key.", e);
        }
    }

    private String readPemText(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            ClassPathResource resource = new ClassPathResource(path.substring("classpath:".length()));
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        }
        return Files.readString(Path.of(path));
    }

    /** Supports both PKCS#1 ("RSA PRIVATE KEY") and PKCS#8 ("PRIVATE KEY") PEM formats. */
    private PrivateKey parsePrivateKey(String pemText) throws IOException {
        try (PEMParser pemParser = new PEMParser(new StringReader(pemText))) {
            Object parsed = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair).getPrivate();
            }
            if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            throw new DocusignAuthenticationException(
                    "Unsupported private key format - expected a PEM-encoded RSA private key (PKCS#1 or PKCS#8).");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
