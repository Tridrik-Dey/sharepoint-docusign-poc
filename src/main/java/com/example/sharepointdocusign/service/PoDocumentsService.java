package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.dto.PoDocumentsResponse.DocumentPayload;
import com.example.sharepointdocusign.model.SharePointDocument;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * Retrieves SharePoint documents for a PO number/revision and hands them
 * back as plain data (base64-encoded content) - no DocuSign or envelope
 * logic involved. Reuses the exact same SharePointDocumentService (mock or
 * real, depending on active profile) that the envelope flow already uses.
 */
@Service
public class PoDocumentsService {

    private final SharePointDocumentService sharePointDocumentService;

    public PoDocumentsService(SharePointDocumentService sharePointDocumentService) {
        this.sharePointDocumentService = sharePointDocumentService;
    }

    public List<DocumentPayload> fetchDocumentPayloads(String poNumber, String revision) {
        List<SharePointDocument> documents = sharePointDocumentService.fetchDocuments(poNumber, revision);
        return documents.stream()
                .map(this::toPayload)
                .toList();
    }

    private DocumentPayload toPayload(SharePointDocument document) {
        String contentBase64 = Base64.getEncoder().encodeToString(document.content());
        return new DocumentPayload(
                document.fileName(), document.contentType(), document.size(), document.sha256(), contentBase64);
    }
}
