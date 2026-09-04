package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.DocusignClient;
import com.example.sharepointdocusign.model.EnvelopeStatusResult;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Handles a DocuSign Connect "envelope completed" notification: re-fetches
 * the envelope's authoritative status and custom fields directly from
 * DocuSign (never trusting the webhook payload itself beyond the envelope
 * id), and if it's genuinely complete with the SAP_PO_NUMBER/SAP_PO_REVISION
 * custom fields this app always stamps on envelopes it creates, downloads
 * the signed document and stores it back into the matching SharePoint
 * folder. Anything else (wrong status, missing fields) is a safe no-op.
 */
@Service
@Profile("!mock & !sharepoint-test")
public class DocusignEnvelopeCompletionService {

    private static final Logger log = LoggerFactory.getLogger(DocusignEnvelopeCompletionService.class);
    private static final String COMPLETED_STATUS = "completed";
    private static final String PO_NUMBER_FIELD = "SAP_PO_NUMBER";
    private static final String REVISION_FIELD = "SAP_PO_REVISION";

    private final DocusignClient docusignClient;
    private final SharePointDocumentService sharePointDocumentService;

    public DocusignEnvelopeCompletionService(DocusignClient docusignClient, SharePointDocumentService sharePointDocumentService) {
        this.docusignClient = docusignClient;
        this.sharePointDocumentService = sharePointDocumentService;
    }

    public void processEnvelopeCompletion(String envelopeId) {
        EnvelopeStatusResult status = docusignClient.getEnvelopeWithCustomFields(envelopeId);

        if (!COMPLETED_STATUS.equalsIgnoreCase(status.status())) {
            log.info("Ignoring DocuSign envelope {} - status is '{}', not completed", envelopeId, status.status());
            return;
        }

        String poNumber = status.customField(PO_NUMBER_FIELD);
        String revision = status.customField(REVISION_FIELD);
        if (poNumber == null || poNumber.isBlank() || revision == null || revision.isBlank()) {
            log.warn("Ignoring completed DocuSign envelope {} - missing {}/{} custom fields, cannot determine SharePoint destination",
                    envelopeId, PO_NUMBER_FIELD, REVISION_FIELD);
            return;
        }

        byte[] combinedDocument = docusignClient.downloadCombinedDocument(envelopeId);
        String fileName = "Signed-PO-" + poNumber + "-" + revision + ".pdf";

        SharePointUploadResult result = sharePointDocumentService.uploadDocument(
                poNumber, revision, fileName, combinedDocument, "application/pdf");

        log.info("Stored signed DocuSign envelope {} into SharePoint poNumber={} revision={} fileName={} size={} sha256={} renamed={}",
                envelopeId, poNumber, revision, result.fileName(), result.size(), result.sha256(), result.renamed());
    }
}
