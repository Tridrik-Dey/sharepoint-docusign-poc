package com.example.sharepointdocusign.client;

import com.example.sharepointdocusign.config.MicrosoftGraphProperties;
import com.example.sharepointdocusign.exception.SharePointAccessException;
import com.example.sharepointdocusign.exception.SharePointAuthenticationException;
import com.example.sharepointdocusign.exception.SharePointDownloadFailedException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.service.MicrosoftTokenService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around the Microsoft Graph endpoints used to browse and
 * download SharePoint documents. Handles @odata.nextLink pagination and
 * maps Graph HTTP errors to the application's domain exceptions - raw Graph
 * response bodies are never propagated to API callers.
 */
@Component
@Profile("!mock")
public class MicrosoftGraphClient {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphClient.class);

    private final WebClient graphWebClient;
    private final MicrosoftTokenService tokenService;
    private final MicrosoftGraphProperties properties;

    public MicrosoftGraphClient(WebClient graphWebClient, MicrosoftTokenService tokenService, MicrosoftGraphProperties properties) {
        this.graphWebClient = graphWebClient;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /**
     * Lists all children of a drive folder, transparently following
     * @odata.nextLink pagination until every page has been retrieved.
     */
    public List<GraphDriveItem> listChildren(String folderPath) {
        String firstPageUri = "/v1.0/drives/" + properties.sharepointDriveId() + "/root:/" + folderPath + ":/children";

        List<GraphDriveItem> allItems = new ArrayList<>();
        String nextUri = firstPageUri;
        boolean firstPage = true;

        while (nextUri != null) {
            GraphChildrenResponse page = fetchChildrenPage(nextUri, firstPage, folderPath);
            if (page.value() != null) {
                allItems.addAll(page.value());
            }
            nextUri = page.nextLink();
            firstPage = false;
        }
        log.debug("Listed {} total drive item(s) under folder path '{}'", allItems.size(), folderPath);
        return allItems;
    }

    /**
     * Downloads the binary content of a drive item, following the redirect
     * Graph issues to a pre-authenticated download URL (see WebClientConfig).
     */
    public byte[] downloadContent(String itemId, String displayName) {
        String uri = "/v1.0/drives/" + properties.sharepointDriveId() + "/items/" + itemId + "/content";
        try {
            return graphWebClient.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(tokenService.getAccessToken()))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            throw mapGraphError(e, "download document " + displayName);
        }
    }

    private GraphChildrenResponse fetchChildrenPage(String uriOrAbsoluteLink, boolean isRelativeToBaseUrl, String folderPath) {
        try {
            WebClient.RequestHeadersSpec<?> request = isRelativeToBaseUrl
                    ? graphWebClient.get().uri(uriOrAbsoluteLink)
                    : graphWebClient.get().uri(URI.create(uriOrAbsoluteLink));
            return request
                    .headers(headers -> headers.setBearerAuth(tokenService.getAccessToken()))
                    .retrieve()
                    .bodyToMono(GraphChildrenResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw mapGraphError(e, "list folder '" + folderPath + "'");
        }
    }

    private RuntimeException mapGraphError(WebClientResponseException e, String action) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        log.warn("Microsoft Graph call failed while trying to {} (HTTP {})", action, e.getStatusCode().value());

        if (status == HttpStatus.NOT_FOUND) {
            return new SharePointFolderNotFoundException("SharePoint folder or item was not found while trying to " + action + ".");
        }
        if (status == HttpStatus.FORBIDDEN) {
            return new SharePointAccessException("Access to SharePoint was denied while trying to " + action + ".", e);
        }
        if (status == HttpStatus.UNAUTHORIZED) {
            return new SharePointAuthenticationException("Microsoft Graph rejected the access token while trying to " + action + ".", e);
        }
        return new SharePointDownloadFailedException(
                "Microsoft Graph request failed while trying to " + action + " (HTTP " + e.getStatusCode().value() + ").", e);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphChildrenResponse(
            List<GraphDriveItem> value,
            @JsonProperty("@odata.nextLink") String nextLink) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphDriveItem(String id, String name, Long size, GraphFileFacet file, Object folder) {

        public boolean isFolder() {
            return folder != null;
        }

        public String mimeType() {
            return (file != null) ? file.mimeType() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphFileFacet(String mimeType) {
    }
}
