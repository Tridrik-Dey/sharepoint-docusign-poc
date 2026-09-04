package com.example.sharepointdocusign.client;

import com.example.sharepointdocusign.config.MicrosoftGraphProperties;
import com.example.sharepointdocusign.exception.SharePointAccessException;
import com.example.sharepointdocusign.exception.SharePointAuthenticationException;
import com.example.sharepointdocusign.exception.SharePointDownloadFailedException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.exception.SharePointUploadFailedException;
import com.example.sharepointdocusign.service.MicrosoftTokenService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /** Graph's simple (non-resumable) upload endpoint only accepts files up to this size. */
    public static final long SIMPLE_UPLOAD_MAX_CONTENT_BYTES = 4L * 1024 * 1024;

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

    /**
     * Uploads content to {folderPath}/{fileName}, creating the folder (and any
     * missing parent segment) first if it doesn't exist yet. Graph auto-renames
     * on a name collision ({@code conflictBehavior=rename}) rather than this
     * app implementing its own versioning - the returned item's name reflects
     * whatever name was actually used.
     */
    public GraphDriveItem uploadContent(String folderPath, String fileName, byte[] content, String contentType) {
        try {
            return putContent(folderPath, fileName, content, contentType);
        } catch (SharePointFolderNotFoundException notFound) {
            log.info("Target SharePoint folder '{}' does not exist yet - creating it before retrying the upload", folderPath);
            ensureFolderPath(folderPath);
            return putContent(folderPath, fileName, content, contentType);
        }
    }

    private GraphDriveItem putContent(String folderPath, String fileName, byte[] content, String contentType) {
        String encodedFileName = UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8);
        String uri = "/v1.0/drives/" + properties.sharepointDriveId() + "/root:/" + folderPath + "/" + encodedFileName
                + ":/content?@microsoft.graph.conflictBehavior=rename";
        try {
            return graphWebClient.put()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(tokenService.getAccessToken()))
                    .contentType(MediaType.valueOf((contentType != null && !contentType.isBlank()) ? contentType : "application/pdf"))
                    .bodyValue(content)
                    .retrieve()
                    .bodyToMono(GraphDriveItem.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw mapGraphError(e, "upload document '" + fileName + "' to folder '" + folderPath + "'", true);
        }
    }

    /**
     * Walks each segment of folderPath (e.g. "4500000105", "02"), creating
     * any that don't already exist. A 409 (already exists) is treated as
     * success, so two callers racing to create the same brand-new folder both
     * end up succeeding.
     */
    private void ensureFolderPath(String folderPath) {
        String parentPath = null;
        for (String segment : folderPath.split("/")) {
            createFolderIfMissing(parentPath, segment);
            parentPath = (parentPath == null) ? segment : parentPath + "/" + segment;
        }
    }

    private void createFolderIfMissing(String parentPath, String folderName) {
        String childrenUri = (parentPath == null)
                ? "/v1.0/drives/" + properties.sharepointDriveId() + "/root/children"
                : "/v1.0/drives/" + properties.sharepointDriveId() + "/root:/" + parentPath + ":/children";
        try {
            graphWebClient.post()
                    .uri(childrenUri)
                    .headers(headers -> headers.setBearerAuth(tokenService.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateFolderRequest(folderName, Map.of(), "fail"))
                    .retrieve()
                    .bodyToMono(GraphDriveItem.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                log.debug("SharePoint folder '{}' already exists under '{}' - continuing", folderName, parentPath);
                return;
            }
            throw mapGraphError(e, "create SharePoint folder '" + folderName + "'", true);
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
        return mapGraphError(e, action, false);
    }

    /**
     * @param isUpload when true, an otherwise-unrecognized failure is reported as
     *                 SharePointUploadFailedException instead of the default
     *                 SharePointDownloadFailedException, so error codes stay accurate
     *                 for write calls (uploadContent, folder creation).
     */
    private RuntimeException mapGraphError(WebClientResponseException e, String action, boolean isUpload) {
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
        if (isUpload) {
            return new SharePointUploadFailedException(
                    "Microsoft Graph request failed while trying to " + action + " (HTTP " + e.getStatusCode().value() + ").", e);
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

    /** Body for Graph's "create folder" call (POST .../children). */
    private record CreateFolderRequest(
            String name,
            Map<String, Object> folder,
            @JsonProperty("@microsoft.graph.conflictBehavior") String conflictBehavior) {
    }
}
