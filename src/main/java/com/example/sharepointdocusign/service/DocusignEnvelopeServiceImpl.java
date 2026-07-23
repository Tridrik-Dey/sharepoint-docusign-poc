package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.DocusignClient;
import com.example.sharepointdocusign.client.DocusignClient.CustomFields;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeDefinitionRequest;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeDocumentPayload;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeResponse;
import com.example.sharepointdocusign.client.DocusignClient.Recipients;
import com.example.sharepointdocusign.client.DocusignClient.SignHereTab;
import com.example.sharepointdocusign.client.DocusignClient.Signer;
import com.example.sharepointdocusign.client.DocusignClient.Tabs;
import com.example.sharepointdocusign.client.DocusignClient.TextCustomField;
import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.model.EnvelopeCreationResult;
import com.example.sharepointdocusign.model.EnvelopeDocument;
import com.example.sharepointdocusign.util.LogMaskingUtil;
import com.example.sharepointdocusign.util.SharePointPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * Real DocusignEnvelopeService: assembles the envelope definition (document
 * order, vendor signer, anchor-based SignHere tab on the main document only,
 * SAP custom fields) and delegates the HTTP call to DocusignClient. Inactive
 * on "sharepoint-test" as well as "mock" - that profile pairs real SharePoint
 * retrieval with MockDocusignEnvelopeService, so no real DocuSign wiring
 * (and no DocuSign credentials) are needed.
 */
@Service
@Profile("!mock & !sharepoint-test")
public class DocusignEnvelopeServiceImpl implements DocusignEnvelopeService {

    private static final Logger log = LoggerFactory.getLogger(DocusignEnvelopeServiceImpl.class);
    private static final String VENDOR_SIGNATURE_ANCHOR = "/vendor-signature/";
    private static final String ANCHOR_UNITS = "pixels";
    private static final String ANCHOR_X_OFFSET = "0";
    private static final String ANCHOR_Y_OFFSET = "-10";

    private final DocusignClient docusignClient;
    private final boolean sendEnvelope;

    public DocusignEnvelopeServiceImpl(DocusignClient docusignClient, ApplicationProperties applicationProperties) {
        this.docusignClient = docusignClient;
        this.sendEnvelope = applicationProperties.docusign().sendEnvelope();
    }

    @Override
    public EnvelopeCreationResult createAndSendEnvelope(
            String poNumber, String revision, String vendorName, String vendorEmail, List<EnvelopeDocument> documents) {

        String folderPath = SharePointPaths.buildFolderPath(poNumber, revision);
        String requestedStatus = sendEnvelope ? "sent" : "created";

        EnvelopeDefinitionRequest envelopeDefinition = new EnvelopeDefinitionRequest(
                "PO " + poNumber + " Revision " + revision + " - Signature Request",
                "Please review and sign Purchase Order " + poNumber + ", revision " + revision + ".",
                buildDocumentPayloads(documents),
                buildRecipients(vendorName, vendorEmail, documents),
                buildCustomFields(poNumber, revision, folderPath),
                requestedStatus);

        EnvelopeResponse response = docusignClient.createEnvelope(envelopeDefinition);

        List<String> documentNames = documents.stream().map(EnvelopeDocument::name).toList();
        log.info("DocuSign envelope envelopeId={} status={} poNumber={} revision={} vendor={} documentCount={}",
                response.envelopeId(), response.status(), poNumber, revision,
                LogMaskingUtil.maskEmail(vendorEmail), documents.size());

        return new EnvelopeCreationResult(response.envelopeId(), response.status(), documentNames, List.of());
    }

    private List<EnvelopeDocumentPayload> buildDocumentPayloads(List<EnvelopeDocument> documents) {
        return documents.stream()
                .map(doc -> new EnvelopeDocumentPayload(
                        String.valueOf(doc.documentId()),
                        doc.name(),
                        "pdf",
                        Base64.getEncoder().encodeToString(doc.content())))
                .toList();
    }

    private Recipients buildRecipients(String vendorName, String vendorEmail, List<EnvelopeDocument> documents) {
        String mainDocumentId = documents.stream()
                .filter(EnvelopeDocument::mainDocument)
                .findFirst()
                .map(doc -> String.valueOf(doc.documentId()))
                .orElse("1");

        SignHereTab signHereTab = new SignHereTab(
                mainDocumentId, VENDOR_SIGNATURE_ANCHOR, ANCHOR_UNITS, ANCHOR_X_OFFSET, ANCHOR_Y_OFFSET, false);
        Signer signer = new Signer(vendorEmail, vendorName, "1", "1", new Tabs(List.of(signHereTab)));
        return new Recipients(List.of(signer));
    }

    private CustomFields buildCustomFields(String poNumber, String revision, String folderPath) {
        return new CustomFields(List.of(
                new TextCustomField("SAP_PO_NUMBER", poNumber, "false"),
                new TextCustomField("SAP_PO_REVISION", revision, "false"),
                new TextCustomField("SHAREPOINT_FOLDER_PATH", folderPath, "false")));
    }
}
