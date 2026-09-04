package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.DocusignClient;
import com.example.sharepointdocusign.exception.DocusignEnvelopeException;
import com.example.sharepointdocusign.model.EnvelopeStatusResult;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocusignEnvelopeCompletionServiceTest {

    @Mock
    private DocusignClient docusignClient;

    @Mock
    private SharePointDocumentService sharePointDocumentService;

    private DocusignEnvelopeCompletionService service() {
        return new DocusignEnvelopeCompletionService(docusignClient, sharePointDocumentService);
    }

    @Test
    void nonCompletedStatusIsANoOp() {
        when(docusignClient.getEnvelopeWithCustomFields("env-1"))
                .thenReturn(new EnvelopeStatusResult("env-1", "sent", Map.of()));

        service().processEnvelopeCompletion("env-1");

        verify(sharePointDocumentService, never()).uploadDocument(any(), any(), any(), any(), any());
        verify(docusignClient, never()).downloadCombinedDocument(any());
    }

    @Test
    void completedWithMissingCustomFieldsIsANoOp() {
        when(docusignClient.getEnvelopeWithCustomFields("env-2"))
                .thenReturn(new EnvelopeStatusResult("env-2", "completed", Map.of("SAP_PO_NUMBER", "4500000105")));

        service().processEnvelopeCompletion("env-2");

        verify(sharePointDocumentService, never()).uploadDocument(any(), any(), any(), any(), any());
        verify(docusignClient, never()).downloadCombinedDocument(any());
    }

    @Test
    void completedWithBothCustomFieldsDownloadsAndUploadsTheSignedDocument() {
        when(docusignClient.getEnvelopeWithCustomFields("env-3")).thenReturn(new EnvelopeStatusResult(
                "env-3", "completed", Map.of("SAP_PO_NUMBER", "4500000105", "SAP_PO_REVISION", "02")));
        byte[] combined = "%PDF-1.4\nsigned\n%%EOF".getBytes();
        when(docusignClient.downloadCombinedDocument("env-3")).thenReturn(combined);
        when(sharePointDocumentService.uploadDocument(
                eq("4500000105"), eq("02"), eq("Signed-PO-4500000105-02.pdf"), eq(combined), eq("application/pdf")))
                .thenReturn(new SharePointUploadResult("item-1", "Signed-PO-4500000105-02.pdf", combined.length, "sha", false));

        service().processEnvelopeCompletion("env-3");

        verify(sharePointDocumentService).uploadDocument(
                "4500000105", "02", "Signed-PO-4500000105-02.pdf", combined, "application/pdf");
    }

    @Test
    void propagatesExceptionsFromDocusignClient() {
        when(docusignClient.getEnvelopeWithCustomFields("env-4"))
                .thenThrow(new DocusignEnvelopeException("boom"));

        assertThatThrownBy(() -> service().processEnvelopeCompletion("env-4"))
                .isInstanceOf(DocusignEnvelopeException.class);
    }
}
