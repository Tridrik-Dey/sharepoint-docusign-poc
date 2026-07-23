package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeMetadata;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeResponse;
import com.example.sharepointdocusign.exception.DocumentTooLargeException;
import com.example.sharepointdocusign.exception.TooManyDocumentsException;
import com.example.sharepointdocusign.exception.TotalEnvelopeSizeExceededException;
import com.example.sharepointdocusign.model.EnvelopeCreationResult;
import com.example.sharepointdocusign.model.EnvelopeDocument;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.util.FileValidationUtil;
import com.example.sharepointdocusign.util.HashUtil;
import com.example.sharepointdocusign.util.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates the full PO-envelope flow: validate the main document, fetch
 * SharePoint supporting documents, enforce size/count limits, then create
 * the DocuSign envelope. Fails atomically - no partial envelope is ever
 * created if a prior step fails.
 */
@Service
public class PoEnvelopeOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(PoEnvelopeOrchestrationService.class);

    private final SharePointDocumentService sharePointDocumentService;
    private final DocusignEnvelopeService docusignEnvelopeService;
    private final ApplicationProperties.Documents documentLimits;

    public PoEnvelopeOrchestrationService(
            SharePointDocumentService sharePointDocumentService,
            DocusignEnvelopeService docusignEnvelopeService,
            ApplicationProperties applicationProperties) {
        this.sharePointDocumentService = sharePointDocumentService;
        this.docusignEnvelopeService = docusignEnvelopeService;
        this.documentLimits = applicationProperties.documents();
    }

    public CreatePoEnvelopeResponse processPoEnvelopeRequest(
            CreatePoEnvelopeMetadata metadata, MultipartFile purchaseOrderDocument, String correlationId) {

        String poNumber = metadata.poNumber();
        String revision = metadata.revision();

        byte[] mainDocumentContent = readBytes(purchaseOrderDocument);
        String mainDocumentName = FileValidationUtil.sanitizeFilename(purchaseOrderDocument.getOriginalFilename());
        FileValidationUtil.validatePdf(mainDocumentContent, purchaseOrderDocument.getContentType(), mainDocumentName);
        if (mainDocumentContent.length > documentLimits.maxMainDocumentSizeBytes()) {
            throw new DocumentTooLargeException(
                    "Main PO document " + mainDocumentName + " exceeds the maximum allowed size of "
                            + documentLimits.maxMainDocumentSizeMb() + " MB.");
        }
        String mainDocumentSha256 = HashUtil.sha256(mainDocumentContent);
        log.info("Main PO document validated name={} size={} sha256={}",
                mainDocumentName, mainDocumentContent.length, mainDocumentSha256);

        List<SharePointDocument> sharePointDocuments = sharePointDocumentService.fetchDocuments(poNumber, revision);

        if (sharePointDocuments.size() > documentLimits.maxSupportingDocuments()) {
            throw new TooManyDocumentsException(
                    "Found " + sharePointDocuments.size() + " supporting documents, exceeding the maximum of "
                            + documentLimits.maxSupportingDocuments() + ".");
        }

        long totalSize = mainDocumentContent.length;
        for (SharePointDocument doc : sharePointDocuments) {
            if (doc.size() > documentLimits.maxSharepointDocumentSizeBytes()) {
                throw new DocumentTooLargeException(
                        "SharePoint document " + doc.fileName() + " exceeds the maximum allowed size of "
                                + documentLimits.maxSharepointDocumentSizeMb() + " MB.");
            }
            totalSize += doc.size();
        }
        if (totalSize > documentLimits.maxTotalEnvelopeSizeBytes()) {
            throw new TotalEnvelopeSizeExceededException(
                    "Total envelope size of " + totalSize + " bytes exceeds the maximum allowed of "
                            + documentLimits.maxTotalEnvelopeSizeMb() + " MB.");
        }

        List<EnvelopeDocument> envelopeDocuments = buildEnvelopeDocuments(
                mainDocumentName, mainDocumentContent, purchaseOrderDocument.getContentType(), sharePointDocuments);

        EnvelopeCreationResult result = docusignEnvelopeService.createAndSendEnvelope(
                poNumber, revision, metadata.vendorName(), metadata.vendorEmail(), envelopeDocuments);

        log.info("Envelope created envelopeId={} status={} poNumber={} revision={} vendor={} documentCount={}",
                result.envelopeId(), result.status(), poNumber, revision,
                LogMaskingUtil.maskEmail(metadata.vendorEmail()), envelopeDocuments.size());

        return CreatePoEnvelopeResponse.success(
                poNumber, revision, result.envelopeId(), result.status(),
                result.documentsIncluded(), result.warnings(), correlationId);
    }

    private List<EnvelopeDocument> buildEnvelopeDocuments(
            String mainDocumentName,
            byte[] mainDocumentContent,
            String mainDocumentContentType,
            List<SharePointDocument> sharePointDocuments) {
        List<EnvelopeDocument> documents = new ArrayList<>();
        documents.add(new EnvelopeDocument(1, mainDocumentName, mainDocumentContent, mainDocumentContentType, true));
        int documentId = 2;
        for (SharePointDocument doc : sharePointDocuments) {
            documents.add(new EnvelopeDocument(documentId++, doc.fileName(), doc.content(), doc.contentType(), false));
        }
        return documents;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded purchase order document.", e);
        }
    }
}
