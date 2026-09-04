package com.example.sharepointdocusign.wiremock;

import com.example.sharepointdocusign.client.MicrosoftGraphClient;
import com.example.sharepointdocusign.client.MicrosoftGraphClient.GraphDriveItem;
import com.example.sharepointdocusign.config.MicrosoftGraphProperties;
import com.example.sharepointdocusign.exception.SharePointAccessException;
import com.example.sharepointdocusign.exception.SharePointAuthenticationException;
import com.example.sharepointdocusign.exception.SharePointDownloadFailedException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.exception.SharePointUploadFailedException;
import com.example.sharepointdocusign.service.MicrosoftTokenService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
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
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises MicrosoftGraphClient against a WireMock stand-in for Microsoft
 * Graph: list children, @odata.nextLink pagination, file download with
 * redirect-following, and 404/403 error mapping.
 */
class MicrosoftGraphClientWireMockTest {

    private WireMockServer wireMockServer;
    private MicrosoftGraphClient graphClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        MicrosoftGraphProperties properties = new MicrosoftGraphProperties(
                "test-tenant", "test-client", "test-secret",
                "test.sharepoint.com", "/sites/Test", "test-drive-id",
                "http://localhost:" + wireMockServer.port(),
                "http://localhost:" + wireMockServer.port() + "/test-tenant/oauth2/v2.0/token",
                "PO-Documents", 5000, 10000, 15000);

        MicrosoftTokenService tokenService = mock(MicrosoftTokenService.class);
        when(tokenService.getAccessToken()).thenReturn("fake-token");

        WebClient graphWebClient = buildGraphWebClient(properties.graphBaseUrl());
        graphClient = new MicrosoftGraphClient(graphWebClient, tokenService, properties);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private WebClient buildGraphWebClient(String baseUrl) {
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
    void listsChildrenFromSinglePage() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(okJson("""
                        {
                          "value": [
                            {"id": "item-1", "name": "Technical-Specification.pdf", "size": 100, "file": {"mimeType": "application/pdf"}},
                            {"id": "item-2", "name": "SubFolder", "folder": {"childCount": 0}}
                          ]
                        }
                        """)));

        List<GraphDriveItem> items = graphClient.listChildren("4500000105/02");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("Technical-Specification.pdf");
        assertThat(items.get(0).isFolder()).isFalse();
        assertThat(items.get(1).isFolder()).isTrue();
    }

    @Test
    void followsODataNextLinkPagination() {
        String nextLink = "http://localhost:" + wireMockServer.port()
                + "/v1.0/drives/test-drive-id/root:/4500000105/02:/children?%24skiptoken=page2";

        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(okJson("""
                        {
                          "value": [ {"id": "item-1", "name": "Commercial-Conditions.pdf", "file": {"mimeType": "application/pdf"}} ],
                          "@odata.nextLink": "%s"
                        }
                        """.formatted(nextLink))));

        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children?%24skiptoken=page2"))
                .willReturn(okJson("""
                        {
                          "value": [ {"id": "item-2", "name": "Technical-Specification.pdf", "file": {"mimeType": "application/pdf"}} ]
                        }
                        """)));

        List<GraphDriveItem> items = graphClient.listChildren("4500000105/02");

        assertThat(items).extracting(GraphDriveItem::name)
                .containsExactlyInAnyOrder("Commercial-Conditions.pdf", "Technical-Specification.pdf");
    }

    @Test
    void mapsGraph404ToFolderNotFoundException() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/0000000000/01:/children"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":{\"code\":\"itemNotFound\"}}")));

        assertThatThrownBy(() -> graphClient.listChildren("0000000000/01"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }

    @Test
    void mapsGraph403ToAccessDeniedException() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(aResponse().withStatus(403).withBody("{\"error\":{\"code\":\"accessDenied\"}}")));

        assertThatThrownBy(() -> graphClient.listChildren("4500000105/02"))
                .isInstanceOf(SharePointAccessException.class);
    }

    @Test
    void mapsGraph401ToAuthenticationFailedException() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":{\"code\":\"InvalidAuthenticationToken\"}}")));

        assertThatThrownBy(() -> graphClient.listChildren("4500000105/02"))
                .isInstanceOf(SharePointAuthenticationException.class);
    }

    @Test
    void listsEmptyChildrenWhenFolderHasNoItems() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02:/children"))
                .willReturn(okJson("""
                        { "value": [] }
                        """)));

        List<GraphDriveItem> items = graphClient.listChildren("4500000105/02");

        assertThat(items).isEmpty();
    }

    @Test
    void downloadsFileContentFollowingRedirect() {
        byte[] pdfBytes = "%PDF-1.4\n%%EOF".getBytes();

        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/item-1/content"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "/download/item-1-bytes")));

        stubFor(get(urlEqualTo("/download/item-1-bytes"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfBytes)));

        byte[] downloaded = graphClient.downloadContent("item-1", "Technical-Specification.pdf");

        assertThat(downloaded).isEqualTo(pdfBytes);
    }

    @Test
    void mapsDownloadNotFoundToDomainException() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/missing-item/content"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> graphClient.downloadContent("missing-item", "Missing.pdf"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }

    @Test
    void mapsDownloadServerErrorToDownloadFailedException() {
        stubFor(get(urlEqualTo("/v1.0/drives/test-drive-id/items/item-1/content"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":{\"code\":\"generalException\"}}")));

        assertThatThrownBy(() -> graphClient.downloadContent("item-1", "Technical-Specification.pdf"))
                .isInstanceOf(SharePointDownloadFailedException.class);
    }

    @Test
    void uploadsContentToExistingFolder() {
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-1\",\"name\":\"Doc.pdf\",\"size\":9}")));

        GraphDriveItem uploaded = graphClient.uploadContent("4500000105/02", "Doc.pdf", "%PDF-1.4".getBytes(), "application/pdf");

        assertThat(uploaded.id()).isEqualTo("item-1");
        assertThat(uploaded.name()).isEqualTo("Doc.pdf");
    }

    @Test
    void uploadReturnsGraphsAutoRenamedNameOnCollision() {
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-2\",\"name\":\"Doc 1.pdf\",\"size\":9}")));

        GraphDriveItem uploaded = graphClient.uploadContent("4500000105/02", "Doc.pdf", "%PDF-1.4".getBytes(), "application/pdf");

        assertThat(uploaded.name()).isEqualTo("Doc 1.pdf");
    }

    @Test
    void uploadCreatesMissingFolderPathThenRetries() {
        String uploadUrl = "/v1.0/drives/test-drive-id/root:/9999999999/01/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename";

        stubFor(put(urlEqualTo(uploadUrl))
                .inScenario("upload-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":{\"code\":\"itemNotFound\"}}"))
                .willSetStateTo("folder-created"));

        stubFor(put(urlEqualTo(uploadUrl))
                .inScenario("upload-retry")
                .whenScenarioStateIs("folder-created")
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-3\",\"name\":\"Doc.pdf\",\"size\":9}")));

        stubFor(post(urlEqualTo("/v1.0/drives/test-drive-id/root/children"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"folder-1\",\"name\":\"9999999999\",\"folder\":{}}")));
        stubFor(post(urlEqualTo("/v1.0/drives/test-drive-id/root:/9999999999:/children"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"folder-2\",\"name\":\"01\",\"folder\":{}}")));

        GraphDriveItem uploaded = graphClient.uploadContent("9999999999/01", "Doc.pdf", "%PDF-1.4".getBytes(), "application/pdf");

        assertThat(uploaded.id()).isEqualTo("item-3");
    }

    @Test
    void folderCreateConflictIsTreatedAsAlreadyExisting() {
        String uploadUrl = "/v1.0/drives/test-drive-id/root:/4500000105/02/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename";

        stubFor(put(urlEqualTo(uploadUrl))
                .inScenario("folder-race")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(404))
                .willSetStateTo("folder-created"));
        stubFor(put(urlEqualTo(uploadUrl))
                .inScenario("folder-race")
                .whenScenarioStateIs("folder-created")
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"item-4\",\"name\":\"Doc.pdf\",\"size\":9}")));

        stubFor(post(urlEqualTo("/v1.0/drives/test-drive-id/root/children"))
                .willReturn(aResponse().withStatus(409).withBody("{\"error\":{\"code\":\"nameAlreadyExists\"}}")));
        stubFor(post(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105:/children"))
                .willReturn(aResponse().withStatus(409).withBody("{\"error\":{\"code\":\"nameAlreadyExists\"}}")));

        GraphDriveItem uploaded = graphClient.uploadContent("4500000105/02", "Doc.pdf", "%PDF-1.4".getBytes(), "application/pdf");

        assertThat(uploaded.id()).isEqualTo("item-4");
    }

    @Test
    void mapsUpload403ToAccessDeniedException() {
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(403).withBody("{\"error\":{\"code\":\"accessDenied\"}}")));

        assertThatThrownBy(() -> graphClient.uploadContent("4500000105/02", "Doc.pdf", "%PDF-1.4".getBytes(), "application/pdf"))
                .isInstanceOf(SharePointAccessException.class);
    }

    @Test
    void mapsUpload401ToAuthenticationFailedException() {
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":{\"code\":\"InvalidAuthenticationToken\"}}")));

        assertThatThrownBy(() -> graphClient.uploadContent("4500000105/02", "Doc.pdf", "%PDF-1.4".getBytes(), "application/pdf"))
                .isInstanceOf(SharePointAuthenticationException.class);
    }

    @Test
    void mapsUploadServerErrorToUploadFailedException() {
        stubFor(put(urlEqualTo("/v1.0/drives/test-drive-id/root:/4500000105/02/Doc.pdf:/content?@microsoft.graph.conflictBehavior=rename"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":{\"code\":\"generalException\"}}")));

        assertThatThrownBy(() -> graphClient.uploadContent("4500000105/02", "Doc.pdf", "%PDF-1.4".getBytes(), "application/pdf"))
                .isInstanceOf(SharePointUploadFailedException.class);
    }
}
