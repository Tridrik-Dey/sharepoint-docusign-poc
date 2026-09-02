package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.MicrosoftGraphClient;
import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.dto.PoDocumentsResponse.DocumentPayload;
import com.example.sharepointdocusign.exception.DocumentTooLargeException;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import com.example.sharepointdocusign.util.FileValidationUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;

/**
 * Retrieves and stores SharePoint documents for a PO number/revision - no
 * DocuSign or envelope logic involved. Reuses the exact same
 * SharePointDocumentService (mock or real, depending on active profile) that
 * the envelope flow already uses.
 */
@Service
public class PoDocumentsService {

    private final SharePointDocumentService sharePointDocumentService;
    private final ApplicationProperties.Documents documentLimits;

    public PoDocumentsService(SharePointDocumentService sharePointDocumentService, ApplicationProperties applicationProperties) {
        this.sharePointDocumentService = sharePointDocumentService;
        this.documentLimits = applicationProperties.documents();
    }

    public List<DocumentPayload> fetchDocumentPayloads(String poNumber, String revision) {
        List<SharePointDocument> documents = sharePointDocumentService.fetchDocuments(poNumber, revision);
        return documents.stream()
                .map(this::toPayload)
                .toList();
    }

    /** Flat folder layout: {poNumber} itself contains the documents directly, no revision subfolder. */
    public List<DocumentPayload> fetchDocumentPayloads(String poNumber) {
        List<SharePointDocument> documents = sharePointDocumentService.fetchDocuments(poNumber);
        return documents.stream()
                .map(this::toPayload)
                .toList();
    }

    public SharePointUploadResult uploadDocument(String poNumber, String revision, MultipartFile document) {
        byte[] content = readBytes(document);
        String fileName = FileValidationUtil.sanitizeFilename(document.getOriginalFilename());
        FileValidationUtil.validatePdf(content, document.getContentType(), fileName);
        enforceUploadSizeLimit(content, fileName);
        return sharePointDocumentService.uploadDocument(poNumber, revision, fileName, content, "application/pdf");
    }

    /** Flat folder layout: {poNumber} itself, no revision subfolder. */
    public SharePointUploadResult uploadDocument(String poNumber, MultipartFile document) {
        byte[] content = readBytes(document);
        String fileName = FileValidationUtil.sanitizeFilename(document.getOriginalFilename());
        FileValidationUtil.validatePdf(content, document.getContentType(), fileName);
        enforceUploadSizeLimit(content, fileName);
        return sharePointDocumentService.uploadDocument(poNumber, fileName, content, "application/pdf");
    }

    private DocumentPayload toPayload(SharePointDocument document) {
        String contentBase64 = Base64.getEncoder().encodeToString(document.content());
        return new DocumentPayload(
                document.fileName(), document.contentType(), document.size(), document.sha256(), contentBase64);
    }

    /**
     * Enforced against the lower of the configured business limit
     * (app.documents.max-sharepoint-document-size-mb) and Microsoft Graph's
     * hard 4MB ceiling for its simple (non-resumable) upload endpoint - so a
     * misconfigured business limit can never let a request reach Graph only
     * to fail there with an opaque 502 instead of a clean 400 here.
     */
    private void enforceUploadSizeLimit(byte[] content, String fileName) {
        long effectiveLimit = Math.min(
                documentLimits.maxSharepointDocumentSizeBytes(),
                MicrosoftGraphClient.SIMPLE_UPLOAD_MAX_CONTENT_BYTES);
        if (content.length > effectiveLimit) {
            throw new DocumentTooLargeException(
                    "Document " + fileName + " exceeds the maximum allowed upload size of "
                            + (effectiveLimit / (1024 * 1024)) + " MB.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded document.", e);
        }
    }
}
