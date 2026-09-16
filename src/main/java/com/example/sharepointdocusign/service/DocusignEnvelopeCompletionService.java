package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.DocusignClient;
import com.example.sharepointdocusign.model.EnvelopeStatusResult;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles a DocuSign Connect "envelope completed" notification: re-fetches
 * the envelope's authoritative status and custom fields directly from
 * DocuSign (never trusting the webhook payload itself beyond the envelope
 * id), and if it's genuinely complete with the folder custom field this app
 * always stamps on envelopes it creates, downloads the signed document and
 * stores it back into the matching SharePoint folder. Anything else (wrong
 * status, missing folder field) is a safe no-op.
 *
 * The top-level folder field is mandatory - without it there is nowhere to
 * file the document. The sub-folder field is optional (may itself contain
 * multiple "/"-separated levels for nesting, e.g. "A1/A2"): when it is
 * blank or absent, the document is filed directly in the top-level folder
 * instead of being skipped.
 *
 * The field names are looked up by first match against a small alias list
 * per field, not a single literal string: this app's own auto-created (Part
 * A) envelopes always use the canonical English names, but a human creating
 * an envelope directly in DocuSign (Part B) may have built their template
 * using the Italian names instead, a longer descriptive variant, or an
 * older template still using the original SAP_PO_* names - all of these
 * route identically.
 */
@Service
@Profile("!mock & !sharepoint-test")
public class DocusignEnvelopeCompletionService {

    private static final Logger log = LoggerFactory.getLogger(DocusignEnvelopeCompletionService.class);
    private static final String COMPLETED_STATUS = "completed";
    private static final List<String> PO_NUMBER_FIELD_NAMES =
            List.of("SharePoint Folder", "Cartella SharePoint", "SAP_PO_NUMBER");
    private static final List<String> REVISION_FIELD_NAMES = List.of(
            "SharePoint Sub Folder",
            "SharePoint Sub Folder (optional, use / for nested)",
            "Sottocartella SharePoint",
            "SAP_PO_REVISION");

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

        String poNumber = firstNonBlank(status, PO_NUMBER_FIELD_NAMES);
        if (poNumber == null) {
            log.warn("Ignoring completed DocuSign envelope {} - missing top-level folder custom field (checked {}), cannot determine SharePoint destination",
                    envelopeId, PO_NUMBER_FIELD_NAMES);
            return;
        }
        // Sub-folder is optional: blank/absent means "file directly in the top-level folder", not "skip it".
        String revision = firstNonBlank(status, REVISION_FIELD_NAMES);

        byte[] combinedDocument = docusignClient.downloadCombinedDocument(envelopeId);
        // revision may itself contain "/" for nested levels (e.g. "A1/A2") - safe in a folder path,
        // but not in a filename, so it's flattened to "-" there only.
        String fileNameSuffix = revision == null ? "" : "-" + revision.replace("/", "-");
        String fileName = "Signed-PO-" + poNumber + fileNameSuffix + ".pdf";

        SharePointUploadResult result = revision == null
                ? sharePointDocumentService.uploadDocument(poNumber, fileName, combinedDocument, "application/pdf")
                : sharePointDocumentService.uploadDocument(poNumber, revision, fileName, combinedDocument, "application/pdf");

        log.info("Stored signed DocuSign envelope {} into SharePoint poNumber={} revision={} fileName={} size={} sha256={} renamed={}",
                envelopeId, poNumber, revision, result.fileName(), result.size(), result.sha256(), result.renamed());
    }

    /** Returns the first non-blank value found across the given alias field names, or null if none is set. */
    private static String firstNonBlank(EnvelopeStatusResult status, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            String value = status.customField(fieldName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
