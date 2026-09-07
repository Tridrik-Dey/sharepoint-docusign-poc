package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.dto.PoDocumentsResponse.DocumentPayload;
import com.example.sharepointdocusign.exception.DocumentTooLargeException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.exception.UnsupportedDocumentException;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoDocumentsServiceTest {

    @Mock
    private SharePointDocumentService sharePointDocumentService;

    private static final ApplicationProperties.Documents DEFAULT_LIMITS =
            new ApplicationProperties.Documents(10, 10, 25, 20);

    private PoDocumentsService newService() {
        return newService(DEFAULT_LIMITS);
    }

    private PoDocumentsService newService(ApplicationProperties.Documents limits) {
        ApplicationProperties applicationProperties = new ApplicationProperties(null, limits, null);
        return new PoDocumentsService(sharePointDocumentService, applicationProperties);
    }

    @Test
    void mapsSharePointDocumentsToBase64Payloads() {
        byte[] contentA = "%PDF-1.4\ndoc-a\n%%EOF".getBytes();
        byte[] contentB = "%PDF-1.4\ndoc-b\n%%EOF".getBytes();
        SharePointDocument docA = new SharePointDocument("id-a", "Doc-A.pdf", "application/pdf", contentA.length, contentA, "sha-a");
        SharePointDocument docB = new SharePointDocument("id-b", "Doc-B.pdf", "application/pdf", contentB.length, contentB, "sha-b");
        when(sharePointDocumentService.fetchAllSupportedDocuments("4500000105", "02")).thenReturn(List.of(docA, docB));

        PoDocumentsService service = newService();
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
        when(sharePointDocumentService.fetchAllSupportedDocuments("0000000000", "01"))
                .thenThrow(new SharePointFolderNotFoundException("not found"));

        PoDocumentsService service = newService();

        assertThatThrownBy(() -> service.fetchDocumentPayloads("0000000000", "01"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }

    @Test
    void mapsSharePointDocumentsToBase64PayloadsForFlatFolder() {
        byte[] content = "%PDF-1.4\ndoc\n%%EOF".getBytes();
        SharePointDocument doc = new SharePointDocument("id", "Doc.pdf", "application/pdf", content.length, content, "sha");
        when(sharePointDocumentService.fetchAllSupportedDocuments("4500000233")).thenReturn(List.of(doc));

        PoDocumentsService service = newService();
        List<DocumentPayload> payloads = service.fetchDocumentPayloads("4500000233");

        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0).fileName()).isEqualTo("Doc.pdf");
        assertThat(payloads.get(0).contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(content));
    }

    @Test
    void propagatesExceptionsFromSharePointDocumentServiceForFlatFolder() {
        when(sharePointDocumentService.fetchAllSupportedDocuments("1111111111"))
                .thenThrow(new SharePointFolderNotFoundException("not found"));

        PoDocumentsService service = newService();

        assertThatThrownBy(() -> service.fetchDocumentPayloads("1111111111"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }

    @Test
    void uploadSanitizesFilenameAndForcesPdfContentType() {
        byte[] content = "%PDF-1.4\nnew-doc\n%%EOF".getBytes();
        // The directory component (".." + "/") must be stripped, leaving only the base filename.
        MockMultipartFile file = new MockMultipartFile("document", "../evil name.pdf", "application/pdf", content);
        SharePointUploadResult fakeResult = new SharePointUploadResult("item-1", "evil name.pdf", content.length, "sha", false);
        when(sharePointDocumentService.uploadDocument(eq("4500000105"), eq("02"), eq("evil name.pdf"), any(), eq("application/pdf")))
                .thenReturn(fakeResult);

        PoDocumentsService service = newService();
        SharePointUploadResult result = service.uploadDocument("4500000105", "02", file);

        assertThat(result).isEqualTo(fakeResult);
    }

    @Test
    void uploadFlatFolderDelegatesToFlatOverload() {
        byte[] content = "%PDF-1.4\nnew-doc\n%%EOF".getBytes();
        MockMultipartFile file = new MockMultipartFile("document", "Doc.pdf", "application/pdf", content);
        SharePointUploadResult fakeResult = new SharePointUploadResult("item-1", "Doc.pdf", content.length, "sha", false);
        when(sharePointDocumentService.uploadDocument(eq("4500000233"), eq("Doc.pdf"), any(), eq("application/pdf")))
                .thenReturn(fakeResult);

        PoDocumentsService service = newService();
        SharePointUploadResult result = service.uploadDocument("4500000233", file);

        assertThat(result).isEqualTo(fakeResult);
    }

    @Test
    void uploadRejectsNonPdfContent() {
        MockMultipartFile file = new MockMultipartFile("document", "not-a-pdf.pdf", "application/pdf", "plain text".getBytes());

        PoDocumentsService service = newService();

        assertThatThrownBy(() -> service.uploadDocument("4500000105", "02", file))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void uploadResolvesContentTypeForANonPdfSupportedType() {
        byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile file = new MockMultipartFile("document", "Photo.jpg", "image/jpeg", jpegBytes);
        SharePointUploadResult fakeResult = new SharePointUploadResult("item-1", "Photo.jpg", jpegBytes.length, "sha", false);
        when(sharePointDocumentService.uploadDocument(eq("4500000105"), eq("02"), eq("Photo.jpg"), any(), eq("image/jpeg")))
                .thenReturn(fakeResult);

        PoDocumentsService service = newService();
        SharePointUploadResult result = service.uploadDocument("4500000105", "02", file);

        assertThat(result).isEqualTo(fakeResult);
    }

    @Test
    void uploadRejectsAnUnsupportedFileExtension() {
        MockMultipartFile file = new MockMultipartFile("document", "archive.zip", "application/zip", "zip content".getBytes());

        PoDocumentsService service = newService();

        assertThatThrownBy(() -> service.uploadDocument("4500000105", "02", file))
                .isInstanceOf(com.example.sharepointdocusign.exception.UnsupportedFileTypeException.class);
    }

    @Test
    void uploadResolvesContentTypeForATxtFile() {
        byte[] content = "hello".getBytes();
        MockMultipartFile file = new MockMultipartFile("document", "notes.txt", "text/plain", content);
        SharePointUploadResult fakeResult = new SharePointUploadResult("item-1", "notes.txt", content.length, "sha", false);
        when(sharePointDocumentService.uploadDocument(eq("4500000105"), eq("02"), eq("notes.txt"), any(), eq("text/plain")))
                .thenReturn(fakeResult);

        PoDocumentsService service = newService();
        SharePointUploadResult result = service.uploadDocument("4500000105", "02", file);

        assertThat(result).isEqualTo(fakeResult);
    }

    @Test
    void uploadResolvesContentTypeForAPptxFile() {
        byte[] content = "ooxml-pptx".getBytes();
        MockMultipartFile file = new MockMultipartFile("document", "Slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", content);
        SharePointUploadResult fakeResult = new SharePointUploadResult("item-1", "Slides.pptx", content.length, "sha", false);
        when(sharePointDocumentService.uploadDocument(eq("4500000105"), eq("02"), eq("Slides.pptx"), any(),
                eq("application/vnd.openxmlformats-officedocument.presentationml.presentation")))
                .thenReturn(fakeResult);

        PoDocumentsService service = newService();
        SharePointUploadResult result = service.uploadDocument("4500000105", "02", file);

        assertThat(result).isEqualTo(fakeResult);
    }

    @Test
    void mapsSharePointDocumentsToBase64PayloadsForAMultiLevelSubPath() {
        byte[] content = "%PDF-1.4\nnested-doc\n%%EOF".getBytes();
        SharePointDocument doc = new SharePointDocument("id", "Doc.pdf", "application/pdf", content.length, content, "sha");
        when(sharePointDocumentService.fetchAllSupportedDocuments("4500000233", "A1/A2/A3/A4")).thenReturn(List.of(doc));

        PoDocumentsService service = newService();
        List<DocumentPayload> payloads = service.fetchDocumentPayloads("4500000233", "A1/A2/A3/A4");

        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0).fileName()).isEqualTo("Doc.pdf");
    }

    @Test
    void uploadPassesAMultiLevelSubPathThroughUnchanged() {
        byte[] content = "%PDF-1.4\nnested-doc\n%%EOF".getBytes();
        MockMultipartFile file = new MockMultipartFile("document", "Doc.pdf", "application/pdf", content);
        SharePointUploadResult fakeResult = new SharePointUploadResult("item-1", "Doc.pdf", content.length, "sha", false);
        when(sharePointDocumentService.uploadDocument(eq("4500000233"), eq("A1/A2/A3/A4"), eq("Doc.pdf"), any(), eq("application/pdf")))
                .thenReturn(fakeResult);

        PoDocumentsService service = newService();
        SharePointUploadResult result = service.uploadDocument("4500000233", "A1/A2/A3/A4", file);

        assertThat(result).isEqualTo(fakeResult);
    }

    @Test
    void uploadRejectsContentOverTheConfiguredLimit() {
        // Limit set below Graph's 4MB ceiling so the *configured* limit is the one that trips.
        ApplicationProperties.Documents tightLimits = new ApplicationProperties.Documents(10, 1, 25, 20);
        byte[] content = new byte[2 * 1024 * 1024];
        System.arraycopy("%PDF-1.4\n".getBytes(), 0, content, 0, 9);
        MockMultipartFile file = new MockMultipartFile("document", "Big.pdf", "application/pdf", content);

        PoDocumentsService service = newService(tightLimits);

        assertThatThrownBy(() -> service.uploadDocument("4500000105", "02", file))
                .isInstanceOf(DocumentTooLargeException.class);
    }
}
