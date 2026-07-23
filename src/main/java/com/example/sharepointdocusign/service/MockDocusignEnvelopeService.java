package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.model.EnvelopeCreationResult;
import com.example.sharepointdocusign.model.EnvelopeDocument;
import com.example.sharepointdocusign.util.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Test-double for DocusignEnvelopeService, active on the "mock" profile and
 * on "sharepoint-test" (real SharePoint retrieval + mock DocuSign, so the
 * Graph integration can be validated without DocuSign credentials). Never
 * calls DocuSign - returns a fake envelope id and logs what would have been
 * sent.
 */
@Service
@Profile({"mock", "sharepoint-test"})
public class MockDocusignEnvelopeService implements DocusignEnvelopeService {

    private static final Logger log = LoggerFactory.getLogger(MockDocusignEnvelopeService.class);

    @Override
    public EnvelopeCreationResult createAndSendEnvelope(
            String poNumber,
            String revision,
            String vendorName,
            String vendorEmail,
            List<EnvelopeDocument> documents) {

        String envelopeId = "mock-envelope-" + UUID.randomUUID();
        List<String> documentNames = documents.stream().map(EnvelopeDocument::name).toList();

        log.info(
                "[MOCK] Simulated DocuSign envelope envelopeId={} status=sent poNumber={} revision={} vendor={} documents={}",
                envelopeId, poNumber, revision, LogMaskingUtil.maskEmail(vendorEmail), documentNames);

        return new EnvelopeCreationResult(envelopeId, "sent", documentNames, List.of());
    }
}
