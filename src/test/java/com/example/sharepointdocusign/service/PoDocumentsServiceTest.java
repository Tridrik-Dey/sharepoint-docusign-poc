package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.dto.PoDocumentsResponse.DocumentPayload;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.model.SharePointDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoDocumentsServiceTest {

    @Mock
    private SharePointDocumentService sharePointDocumentService;

    @Test
    void mapsSharePointDocumentsToBase64Payloads() {
        byte[] contentA = "%PDF-1.4\ndoc-a\n%%EOF".getBytes();
        byte[] contentB = "%PDF-1.4\ndoc-b\n%%EOF".getBytes();
        SharePointDocument docA = new SharePointDocument("id-a", "Doc-A.pdf", "application/pdf", contentA.length, contentA, "sha-a");
        SharePointDocument docB = new SharePointDocument("id-b", "Doc-B.pdf", "application/pdf", contentB.length, contentB, "sha-b");
        when(sharePointDocumentService.fetchDocuments("4500000105", "02")).thenReturn(List.of(docA, docB));

        PoDocumentsService service = new PoDocumentsService(sharePointDocumentService);
        List<DocumentPayload> payloads = service.fetchDocumentPayloads("4500000105", "02");

        assertThat(payloads).hasSize(2);
        assertThat(payloads.get(0).fileName()).isEqualTo("Doc-A.pdf");
        assertThat(payloads.get(0).contentType()).isEqualTo("application/pdf");
        assertThat(payloads.get(0).size()).isEqualTo(contentA.length);
        assertThat(payloads.get(0).sha256()).isEqualTo("sha-a");
        assertThat(payloads.get(0).contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(contentA));

        assertThat(payloads.get(1).contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(contentB));
    }

    @Test
    void propagatesExceptionsFromSharePointDocumentService() {
        when(sharePointDocumentService.fetchDocuments("0000000000", "01"))
                .thenThrow(new SharePointFolderNotFoundException("not found"));

        PoDocumentsService service = new PoDocumentsService(sharePointDocumentService);

        assertThatThrownBy(() -> service.fetchDocumentPayloads("0000000000", "01"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }

    @Test
    void mapsSharePointDocumentsToBase64PayloadsForFlatFolder() {
        byte[] content = "%PDF-1.4\ndoc\n%%EOF".getBytes();
        SharePointDocument doc = new SharePointDocument("id", "Doc.pdf", "application/pdf", content.length, content, "sha");
        when(sharePointDocumentService.fetchDocuments("4500000233")).thenReturn(List.of(doc));

        PoDocumentsService service = new PoDocumentsService(sharePointDocumentService);
        List<DocumentPayload> payloads = service.fetchDocumentPayloads("4500000233");

        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0).fileName()).isEqualTo("Doc.pdf");
        assertThat(payloads.get(0).contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(content));
    }

    @Test
    void propagatesExceptionsFromSharePointDocumentServiceForFlatFolder() {
        when(sharePointDocumentService.fetchDocuments("1111111111"))
                .thenThrow(new SharePointFolderNotFoundException("not found"));

        PoDocumentsService service = new PoDocumentsService(sharePointDocumentService);

        assertThatThrownBy(() -> service.fetchDocumentPayloads("1111111111"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }
}
