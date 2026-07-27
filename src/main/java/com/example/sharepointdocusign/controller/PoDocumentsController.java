package com.example.sharepointdocusign.controller;

import com.example.sharepointdocusign.config.CorrelationIdFilter;
import com.example.sharepointdocusign.dto.PoDocumentsResponse;
import com.example.sharepointdocusign.dto.PoDocumentsResponse.DocumentPayload;
import com.example.sharepointdocusign.exception.PoEnvelopeException;
import com.example.sharepointdocusign.service.PoDocumentsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Document-only retrieval endpoint: given a PO number and revision, returns
 * the matching SharePoint documents as base64-encoded content. No DocuSign
 * or envelope logic is involved - intended for callers (e.g. SAP) that want
 * to retrieve the documents themselves and handle them however they need to
 * (e.g. attaching to their own object storage), rather than have this
 * service create a signature envelope.
 */
@RestController
public class PoDocumentsController {

    private static final Logger log = LoggerFactory.getLogger(PoDocumentsController.class);

    private final PoDocumentsService poDocumentsService;

    public PoDocumentsController(PoDocumentsService poDocumentsService) {
        this.poDocumentsService = poDocumentsService;
    }

    @GetMapping("/api/v1/po-documents/{poNumber}/{revision}")
    public ResponseEntity<PoDocumentsResponse> getPoDocuments(
            @PathVariable String poNumber, @PathVariable String revision) {

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.info("Received PO documents request poNumber={} revision={} correlationId={}",
                poNumber, revision, correlationId);

        try {
            List<DocumentPayload> documents = poDocumentsService.fetchDocumentPayloads(poNumber, revision);
            return ResponseEntity.ok(PoDocumentsResponse.success(poNumber, revision, documents, correlationId));
        } catch (PoEnvelopeException ex) {
            log.warn("Request failed with errorCode={} message={}", ex.errorCode(), ex.getMessage());
            return ResponseEntity.status(ex.httpStatus()).body(
                    PoDocumentsResponse.failure(poNumber, revision, ex.errorCode(), ex.getMessage(), correlationId));
        }
    }
}
