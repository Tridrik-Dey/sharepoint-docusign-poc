package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.model.EnvelopeCreationResult;
import com.example.sharepointdocusign.model.EnvelopeDocument;

import java.util.List;

/**
 * Creates (and, depending on configuration, sends) the DocuSign envelope
 * containing the main PO document followed by the SharePoint supporting
 * documents, in the given order.
 */
public interface DocusignEnvelopeService {

    EnvelopeCreationResult createAndSendEnvelope(
            String poNumber,
            String revision,
            String vendorName,
            String vendorEmail,
            List<EnvelopeDocument> documents);
}
