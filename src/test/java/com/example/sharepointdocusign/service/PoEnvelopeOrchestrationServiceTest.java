package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeMetadata;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeResponse;
import com.example.sharepointdocusign.exception.DocumentTooLargeException;
import com.example.sharepointdocusign.exception.DocusignEnvelopeException;
import com.example.sharepointdocusign.exception.SharePointDownloadFailedException;
import com.example.sharepointdocusign.exception.TooManyDocumentsException;
import com.example.sharepointdocusign.exception.TotalEnvelopeSizeExceededException;
import com.example.sharepointdocusign.exception.UnsupportedDocumentException;
import com.example.sharepointdocusign.model.EnvelopeCreationResult;
import com.example.sharepointdocusign.model.EnvelopeDocument;
import com.example.sharepointdocusign.model.SharePointDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoEnvelopeOrchestrationServiceTest {

    private static final byte[] VALID_PDF = "%PDF-1.4\nsample\n%%EOF".getBytes();

    @Mock
    private SharePointDocumentService sharePointDocumentService;

    @Mock
    private DocusignEnvelopeService docusignEnvelopeService;

    private PoEnvelopeOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = newService(new ApplicationProperties.Documents(10, 10, 25, 20));
    }

    private PoEnvelopeOrchestrationService newService(ApplicationProperties.Documents documents) {
        ApplicationProperties applicationProperties =
                new ApplicationProperties(new ApplicationProperties.Docusign(true), documents, null);
        return new PoEnvelopeOrchestrationService(sharePointDocumentService, docusignEnvelopeService, applicationProperties);
    }

    private CreatePoEnvelopeMetadata metadata() {
        return new CreatePoEnvelopeMetadata("4500000105", "02", "Dummy Vendor SRL", "test@example.com");
    }

    private MockMultipartFile mainDocument() {
        return new MockMultipartFile("purchaseOrderDocument", "Purchase-Order-4500000105.pdf", "application/pdf", VALID_PDF);
    }

    @Test
    void successfulOrchestrationReturnsEnvelopeDetails() {
        SharePointDocument doc1 = new SharePointDocument("id-1", "Commercial-Conditions.pdf", "application/pdf", 100, VALID_PDF, "sha-1");
        SharePointDocument doc2 = new SharePointDocument("id-2", "Technical-Specification.pdf", "application/pdf", 100, VALID_PDF, "sha-2");
        when(sharePointDocumentService.fetchDocuments("4500000105", "02")).thenReturn(List.of(doc1, doc2));

        EnvelopeCreationResult result = new EnvelopeCreationResult(
                "envelope-123", "sent",
                List.of("Purchase-Order-4500000105.pdf", "Commercial-Conditions.pdf", "Technical-Specification.pdf"),
                List.of());
        when(docusignEnvelopeService.createAndSendEnvelope(eq("4500000105"), eq("02"), anyString(), anyString(), anyList()))
                .thenReturn(result);

        CreatePoEnvelopeResponse response = orchestrationService.processPoEnvelopeRequest(metadata(), mainDocument(), "corr-1");

        assertThat(response.success()).isTrue();
        assertThat(response.envelopeId()).isEqualTo("envelope-123");
        assertThat(response.status()).isEqualTo("sent");
        assertThat(response.documentsIncluded()).containsExactly(
                "Purchase-Order-4500000105.pdf", "Commercial-Conditions.pdf", "Technical-Specification.pdf");
        assertThat(response.correlationId()).isEqualTo("corr-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EnvelopeDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(docusignEnvelopeService).createAndSendEnvelope(
                eq("4500000105"), eq("02"), eq("Dummy Vendor SRL"), eq("test@example.com"), captor.capture());
        List<EnvelopeDocument> sentDocuments = captor.getValue();
        assertThat(sentDocuments).hasSize(3);
        assertThat(sentDocuments.get(0).mainDocument()).isTrue();
        assertThat(sentDocuments.get(0).documentId()).isEqualTo(1);
        assertThat(sentDocuments.get(1).documentId()).isEqualTo(2);
        assertThat(sentDocuments.get(1).mainDocument()).isFalse();
        assertThat(sentDocuments.get(2).documentId()).isEqualTo(3);
    }

    @Test
    void emptyMainDocumentIsRejected() {
        MockMultipartFile emptyFile = new MockMultipartFile("purchaseOrderDocument", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> orchestrationService.processPoEnvelopeRequest(metadata(), emptyFile, "corr-1"))
                .isInstanceOf(UnsupportedDocumentException.class);

        verifyNoInteractions(sharePointDocumentService, docusignEnvelopeService);
    }

    @Test
    void sharePointDownloadFailurePreventsEnvelopeCreation() {
        when(sharePointDocumentService.fetchDocuments(anyString(), anyString()))
                .thenThrow(new SharePointDownloadFailedException("boom"));

        assertThatThrownBy(() -> orchestrationService.processPoEnvelopeRequest(metadata(), mainDocument(), "corr-1"))
                .isInstanceOf(SharePointDownloadFailedException.class);

        verifyNoInteractions(docusignEnvelopeService);
    }

    @Test
    void tooManyDocumentsIsRejected() {
        List<SharePointDocument> manyDocs = IntStream.range(0, 25)
                .mapToObj(i -> new SharePointDocument("id-" + i, "Doc-" + i + ".pdf", "application/pdf", 10, VALID_PDF, "sha-" + i))
                .toList();
        when(sharePointDocumentService.fetchDocuments(anyString(), anyString())).thenReturn(manyDocs);

        assertThatThrownBy(() -> orchestrationService.processPoEnvelopeRequest(metadata(), mainDocument(), "corr-1"))
                .isInstanceOf(TooManyDocumentsException.class);

        verifyNoInteractions(docusignEnvelopeService);
    }

    @Test
    void documentTooLargeIsRejectedForOversizedSharePointDocument() {
        orchestrationService = newService(new ApplicationProperties.Documents(10, 1, 25, 20));
        byte[] bigContent = oversizedPdfContent(2 * 1024 * 1024);
        SharePointDocument bigDoc = new SharePointDocument("id-1", "Big-Doc.pdf", "application/pdf", bigContent.length, bigContent, "sha-1");
        when(sharePointDocumentService.fetchDocuments(anyString(), anyString())).thenReturn(List.of(bigDoc));

        assertThatThrownBy(() -> orchestrationService.processPoEnvelopeRequest(metadata(), mainDocument(), "corr-1"))
                .isInstanceOf(DocumentTooLargeException.class);

        verifyNoInteractions(docusignEnvelopeService);
    }

    @Test
    void totalEnvelopeSizeExceededIsRejected() {
        orchestrationService = newService(new ApplicationProperties.Documents(10, 10, 1, 20));
        byte[] bigContent = oversizedPdfContent(2 * 1024 * 1024);
        SharePointDocument bigDoc = new SharePointDocument("id-1", "Big-Doc.pdf", "application/pdf", bigContent.length, bigContent, "sha-1");
        when(sharePointDocumentService.fetchDocuments(anyString(), anyString())).thenReturn(List.of(bigDoc));

        assertThatThrownBy(() -> orchestrationService.processPoEnvelopeRequest(metadata(), mainDocument(), "corr-1"))
                .isInstanceOf(TotalEnvelopeSizeExceededException.class);

        verifyNoInteractions(docusignEnvelopeService);
    }

    @Test
    void docusignFailureIsPropagatedWithoutFalseSuccess() {
        when(sharePointDocumentService.fetchDocuments(anyString(), anyString())).thenReturn(List.of());
        when(docusignEnvelopeService.createAndSendEnvelope(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new DocusignEnvelopeException("DocuSign rejected the envelope"));

        assertThatThrownBy(() -> orchestrationService.processPoEnvelopeRequest(metadata(), mainDocument(), "corr-1"))
                .isInstanceOf(DocusignEnvelopeException.class);
    }

    private byte[] oversizedPdfContent(int size) {
        byte[] content = new byte[size];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        return content;
    }
}
