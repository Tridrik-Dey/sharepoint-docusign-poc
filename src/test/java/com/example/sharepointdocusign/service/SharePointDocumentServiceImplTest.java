package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.MicrosoftGraphClient;
import com.example.sharepointdocusign.client.MicrosoftGraphClient.GraphDriveItem;
import com.example.sharepointdocusign.client.MicrosoftGraphClient.GraphFileFacet;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.model.SharePointDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharePointDocumentServiceImplTest {

    private static final byte[] VALID_PDF = "%PDF-1.4\n%%EOF".getBytes();

    @Mock
    private MicrosoftGraphClient graphClient;

    private SharePointDocumentServiceImpl serviceUnderTest() {
        return new SharePointDocumentServiceImpl(graphClient);
    }

    @Test
    void ignoresFoldersAndNonPdfAndTempFilesThenSortsAlphabetically() {
        List<GraphDriveItem> children = List.of(
                new GraphDriveItem("f-1", "SubFolder", null, null, new Object()),
                new GraphDriveItem("id-txt", "notes.txt", 10L, new GraphFileFacet("text/plain"), null),
                new GraphDriveItem("id-tmp", "~$Doc-A.pdf", 10L, new GraphFileFacet("application/pdf"), null),
                new GraphDriveItem("id-b", "Doc-B.pdf", 20L, new GraphFileFacet("application/pdf"), null),
                new GraphDriveItem("id-a", "Doc-A.pdf", 20L, new GraphFileFacet("application/pdf"), null));
        when(graphClient.listChildren("4500000105/REV-02")).thenReturn(children);
        when(graphClient.downloadContent("id-a", "Doc-A.pdf")).thenReturn(VALID_PDF);
        when(graphClient.downloadContent("id-b", "Doc-B.pdf")).thenReturn(VALID_PDF);

        List<SharePointDocument> documents = serviceUnderTest().fetchDocuments("4500000105", "02");

        assertThat(documents).extracting(SharePointDocument::fileName).containsExactly("Doc-A.pdf", "Doc-B.pdf");
        assertThat(documents).extracting(SharePointDocument::sha256).allMatch(sha -> sha != null && !sha.isBlank());
    }

    @Test
    void throwsEmptyFolderWhenNoEligibleItemsRemain() {
        List<GraphDriveItem> children = List.of(
                new GraphDriveItem("f-1", "SubFolder", null, null, new Object()),
                new GraphDriveItem("id-txt", "notes.txt", 10L, new GraphFileFacet("text/plain"), null));
        when(graphClient.listChildren("4500000105/REV-02")).thenReturn(children);

        assertThatThrownBy(() -> serviceUnderTest().fetchDocuments("4500000105", "02"))
                .isInstanceOf(EmptySharePointFolderException.class);
    }
}
