package com.example.sharepointdocusign.wiremock;

import com.example.sharepointdocusign.client.MicrosoftGraphClient;
import com.example.sharepointdocusign.config.MicrosoftGraphProperties;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import com.example.sharepointdocusign.service.MicrosoftTokenService;
import com.example.sharepointdocusign.service.SharePointDocumentServiceImpl;
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

import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end test of the real (non-mock) SharePoint retrieval chain - the
 * actual MicrosoftTokenService, MicrosoftGraphClient and
 * SharePointDocumentServiceImpl all cooperating against one WireMock server
 * standing in for both the Microsoft identity platform and Microsoft Graph.
 * Focuses on the two scenarios that are only meaningful at this combined
 * level: PDF eligibility filtering and empty-folder detection.
 */
class SharePointDocumentServiceImplWireMockTest {

    private WireMockServer wireMockServer;
    private SharePointDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        String baseUrl = "http://localhost:" + wireMockServer.port();
        MicrosoftGraphProperties properties = new MicrosoftGraphProperties(
                "test-tenant", "test-client", "test-secret",
                "test.sharepoint.com", "/sites/Test", "test-drive-id",
                baseUrl, baseUrl + "/test-tenant/oauth2/v2.0/token",
                "PO-Documents", 5000, 10000, 15000);

        stubFor(post(urlPathEqualTo("/test-tenant/oauth2/v2.0/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-graph-token","token_type":"Bearer","expires_in":3600}
                        """)));

        WebClient identityWebClient = plainWebClient(baseUrl);
        MicrosoftTokenService tokenService = new MicrosoftTokenService(identityWebClient, properties);

        WebClient graphWebClient = plainWebClient(baseUrl);
        MicrosoftGraphClient graphClient = new MicrosoftGraphClient(graphWebClient, tokenService, properties);

        service = new SharePointDocumentServiceImpl(graphClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private WebClient plainWebClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5))
                .followRedirect(true);
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
    }

    @Test
    void filtersToOnlyEligiblePdfsAndPreservesChecksumEndToEnd() throws Exception {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(okJson("""
                        {
                          "value": [
                            {"id": "folder-1", "name": "SubFolder", "folder": {"childCount": 0}},
                            {"id": "id-notes", "name": "notes.txt", "size": 10, "file": {"mimeType": "text/plain"}},
                            {"id": "id-tmp", "name": "~$Doc-A.pdf", "size": 10, "file": {"mimeType": "application/pdf"}},
                            {"id": "id-b", "name": "Doc-B.pdf", "size": 14, "file": {"mimeType": "application/pdf"}},
                            {"id": "id-a", "name": "Doc-A.pdf", "size": 14, "file": {"mimeType": "application/pdf"}}
                          ]
                        }
                        """)));

        byte[] docAContent = "%PDF-1.4\n%%EOF".getBytes();
        byte[] docBContent = "%PDF-1.4\nfile-b\n%%EOF".getBytes();
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/id-a/content"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(docAContent)));
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/id-b/content"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(docBContent)));

        List<SharePointDocument> documents = service.fetchDocuments("4500000105", "02");

        assertThat(documents).extracting(SharePointDocument::fileName).containsExactly("Doc-A.pdf", "Doc-B.pdf");
        assertThat(documents.get(0).itemId()).isEqualTo("id-a");
        assertThat(documents.get(0).content()).isEqualTo(docAContent);
        assertThat(documents.get(0).size()).isEqualTo(docAContent.length);
        assertThat(documents.get(0).sha256()).isEqualTo(sha256Hex(docAContent));
        assertThat(documents.get(1).sha256()).isEqualTo(sha256Hex(docBContent));
    }

    @Test
    void throwsEmptyFolderWhenGraphReturnsNoChildrenAtAll() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(okJson("""
                        { "value": [] }
                        """)));

        assertThatThrownBy(() -> service.fetchDocuments("4500000105", "02"))
                .isInstanceOf(EmptySharePointFolderException.class);
    }

    @Test
    void throwsEmptyFolderWhenOnlyIneligibleFilesArePresent() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(okJson("""
                        {
                          "value": [
                            {"id": "folder-1", "name": "SubFolder", "folder": {"childCount": 0}},
                            {"id": "id-notes", "name": "notes.txt", "size": 10, "file": {"mimeType": "text/plain"}},
                            {"id": "id-tmp", "name": "~$Doc-A.pdf", "size": 10, "file": {"mimeType": "application/pdf"}}
                          ]
                        }
                        """)));

        assertThatThrownBy(() -> service.fetchDocuments("4500000105", "02"))
                .isInstanceOf(EmptySharePointFolderException.class);
    }

    @Test
    void uploadsDocumentEndToEndForNestedFolder() throws Exception {
        byte[] content = "%PDF-1.4\nnew-doc\n%%EOF".getBytes();
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02/New-Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-new\",\"name\":\"New-Doc.pdf\",\"size\":" + content.length + "}")));

        SharePointUploadResult result = service.uploadDocument("4500000105", "02", "New-Doc.pdf", content, "application/pdf");

        assertThat(result.itemId()).isEqualTo("item-new");
        assertThat(result.fileName()).isEqualTo("New-Doc.pdf");
        assertThat(result.renamed()).isFalse();
        assertThat(result.sha256()).isEqualTo(sha256Hex(content));
    }

    @Test
    void uploadsDocumentEndToEndForFlatFolder() throws Exception {
        byte[] content = "%PDF-1.4\nflat-doc\n%%EOF".getBytes();
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000233/New-Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-flat\",\"name\":\"New-Doc.pdf\",\"size\":" + content.length + "}")));

        SharePointUploadResult result = service.uploadDocument("4500000233", "New-Doc.pdf", content, "application/pdf");

        assertThat(result.itemId()).isEqualTo("item-flat");
        assertThat(result.renamed()).isFalse();
    }

    @Test
    void uploadReflectsGraphsAutoRenameAsRenamedTrue() {
        byte[] content = "%PDF-1.4\ncollide\n%%EOF".getBytes();
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-renamed\",\"name\":\"Doc 1.pdf\",\"size\":" + content.length + "}")));

        SharePointUploadResult result = service.uploadDocument("4500000105", "02", "Doc.pdf", content, "application/pdf");

        assertThat(result.fileName()).isEqualTo("Doc 1.pdf");
        assertThat(result.renamed()).isTrue();
    }

    @Test
    void fetchAllSupportedDocumentsReturnsNonPdfTypesThatFetchDocumentsWouldExclude() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/6000000000/01:/children"))
                .willReturn(okJson("""
                        {
                          "value": [
                            {"id": "id-pdf", "name": "Spec.pdf", "file": {"mimeType": "application/pdf"}},
                            {"id": "id-docx", "name": "Amendment.docx", "file": {"mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}},
                            {"id": "id-jpg", "name": "Photo.jpg", "file": {"mimeType": "image/jpeg"}},
                            {"id": "id-txt", "name": "notes.txt", "file": {"mimeType": "text/plain"}}
                          ]
                        }
                        """)));

        byte[] pdfBytes = "%PDF-1.4\n%%EOF".getBytes();
        byte[] docxBytes = "fake docx bytes".getBytes();
        byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/id-pdf/content"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(pdfBytes)));
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/id-docx/content"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .withBody(docxBytes)));
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/id-jpg/content"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "image/jpeg").withBody(jpegBytes)));

        List<SharePointDocument> documents = service.fetchAllSupportedDocuments("6000000000", "01");

        // notes.txt is excluded (not in the upload allowlist), even though the folder listing included it -
        // its content is never even downloaded (no stub registered for id-txt, so a download attempt would fail the test).
        assertThat(documents).extracting(SharePointDocument::fileName)
                .containsExactly("Amendment.docx", "Photo.jpg", "Spec.pdf");
        assertThat(documents).extracting(SharePointDocument::contentType).containsExactlyInAnyOrder(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/jpeg", "application/pdf");
    }

    @Test
    void fetchAllSupportedDocumentsResolvesAMultiLevelSubPathAgainstGraph() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000233/A1/A2/A3:/children"))
                .willReturn(okJson("""
                        {
                          "value": [
                            {"id": "id-a4", "name": "Level-A4-Doc.pdf", "file": {"mimeType": "application/pdf"}}
                          ]
                        }
                        """)));

        byte[] content = "%PDF-1.4\nlevel-a4\n%%EOF".getBytes();
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/id-a4/content"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(content)));

        List<SharePointDocument> documents = service.fetchAllSupportedDocuments("4500000233", "A1/A2/A3");

        assertThat(documents).extracting(SharePointDocument::fileName).containsExactly("Level-A4-Doc.pdf");
    }

    @Test
    void uploadsDocumentEndToEndForAMultiLevelSubPath() throws Exception {
        byte[] content = "%PDF-1.4\nnested-doc\n%%EOF".getBytes();
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000233/A1/A2/A3/A4/New-Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-nested\",\"name\":\"New-Doc.pdf\",\"size\":" + content.length + "}")));

        SharePointUploadResult result = service.uploadDocument("4500000233", "A1/A2/A3/A4", "New-Doc.pdf", content, "application/pdf");

        assertThat(result.itemId()).isEqualTo("item-nested");
        assertThat(result.fileName()).isEqualTo("New-Doc.pdf");
        assertThat(result.renamed()).isFalse();
        assertThat(result.sha256()).isEqualTo(sha256Hex(content));
    }

    private String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
