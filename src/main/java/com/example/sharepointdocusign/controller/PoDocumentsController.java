package com.example.sharepointdocusign.controller;

import com.example.sharepointdocusign.config.CorrelationIdFilter;
import com.example.sharepointdocusign.dto.PoDocumentUploadResponse;
import com.example.sharepointdocusign.dto.PoDocumentsResponse;
import com.example.sharepointdocusign.dto.PoDocumentsResponse.DocumentPayload;
import com.example.sharepointdocusign.exception.PoEnvelopeException;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import com.example.sharepointdocusign.service.PoDocumentsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Document-only read/write endpoints: GET returns the matching SharePoint
 * documents for a PO number and revision as base64-encoded content; POST
 * stores one document into that same folder. No DocuSign or envelope logic
 * is involved either way - intended for callers (e.g. SAP) that want to
 * retrieve or push documents themselves (e.g. attaching to their own object
 * storage), rather than have this service create a signature envelope.
 */
@RestController
public class PoDocumentsController {

    private static final Logger log = LoggerFactory.getLogger(PoDocumentsController.class);

    private final PoDocumentsService poDocumentsService;

    public PoDocumentsController(PoDocumentsService poDocumentsService) {
        this.poDocumentsService = poDocumentsService;
    }

    /**
     * subPath may itself contain multiple "/"-separated folder levels (e.g.
     * "A1/A2/A3", not just a single revision value) - SAP fully controls
     * both the depth and the name of each level. The response's "revision"
     * field echoes subPath back exactly as received, whether that's a single
     * value or a multi-level path.
     */
    @GetMapping("/api/v1/po-documents/{poNumber}/{*subPath}")
    public ResponseEntity<PoDocumentsResponse> getPoDocuments(
            @PathVariable String poNumber, @PathVariable String subPath) {

        String cleanSubPath = stripLeadingSlash(subPath);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.info("Received PO documents request poNumber={} subPath={} correlationId={}",
                poNumber, cleanSubPath, correlationId);

        try {
            List<DocumentPayload> documents = poDocumentsService.fetchDocumentPayloads(poNumber, cleanSubPath);
            return ResponseEntity.ok(PoDocumentsResponse.success(poNumber, cleanSubPath, documents, correlationId));
        } catch (PoEnvelopeException ex) {
            log.warn("Request failed with errorCode={} message={}", ex.errorCode(), ex.getMessage());
            return ResponseEntity.status(ex.httpStatus()).body(
                    PoDocumentsResponse.failure(poNumber, cleanSubPath, ex.errorCode(), ex.getMessage(), correlationId));
        }
    }

    /** Flat folder layout: {poNumber} itself contains the documents directly, no revision subfolder. */
    @GetMapping("/api/v1/po-documents/{poNumber}")
    public ResponseEntity<PoDocumentsResponse> getPoDocumentsFlat(@PathVariable String poNumber) {

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.info("Received PO documents request (flat folder) poNumber={} correlationId={}",
                poNumber, correlationId);

        try {
            List<DocumentPayload> documents = poDocumentsService.fetchDocumentPayloads(poNumber);
            return ResponseEntity.ok(PoDocumentsResponse.success(poNumber, null, documents, correlationId));
        } catch (PoEnvelopeException ex) {
            log.warn("Request failed with errorCode={} message={}", ex.errorCode(), ex.getMessage());
            return ResponseEntity.status(ex.httpStatus()).body(
                    PoDocumentsResponse.failure(poNumber, null, ex.errorCode(), ex.getMessage(), correlationId));
        }
    }

    /**
     * Stores one document into the {poNumber}/{subPath} SharePoint folder
     * (creating every missing level first). subPath may itself contain
     * multiple "/"-separated folder levels (e.g. "A1/A2/A3") - SAP fully
     * controls both the depth and the name of each level. One file per
     * call - callers with multiple files call this once per file. No
     * DocuSign or envelope logic is involved. The response's "revision"
     * field echoes subPath back exactly as received.
     */
    @PostMapping(path = "/api/v1/po-documents/{poNumber}/{*subPath}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PoDocumentUploadResponse> uploadPoDocument(
            @PathVariable String poNumber, @PathVariable String subPath, @RequestPart("document") MultipartFile document) {

        String cleanSubPath = stripLeadingSlash(subPath);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.info("Received PO document upload request poNumber={} subPath={} correlationId={}",
                poNumber, cleanSubPath, correlationId);

        try {
            SharePointUploadResult result = poDocumentsService.uploadDocument(poNumber, cleanSubPath, document);
            return ResponseEntity.ok(PoDocumentUploadResponse.success(
                    poNumber, cleanSubPath, result.fileName(), result.size(), result.sha256(), result.renamed(), correlationId));
        } catch (PoEnvelopeException ex) {
            log.warn("Request failed with errorCode={} message={}", ex.errorCode(), ex.getMessage());
            return ResponseEntity.status(ex.httpStatus()).body(
                    PoDocumentUploadResponse.failure(poNumber, cleanSubPath, ex.errorCode(), ex.getMessage(), correlationId));
        }
    }

    /** Flat folder layout: {poNumber} itself, no revision subfolder. */
    @PostMapping(path = "/api/v1/po-documents/{poNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PoDocumentUploadResponse> uploadPoDocumentFlat(
            @PathVariable String poNumber, @RequestPart("document") MultipartFile document) {

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.info("Received PO document upload request (flat folder) poNumber={} correlationId={}",
                poNumber, correlationId);

        try {
            SharePointUploadResult result = poDocumentsService.uploadDocument(poNumber, document);
            return ResponseEntity.ok(PoDocumentUploadResponse.success(
                    poNumber, null, result.fileName(), result.size(), result.sha256(), result.renamed(), correlationId));
        } catch (PoEnvelopeException ex) {
            log.warn("Request failed with errorCode={} message={}", ex.errorCode(), ex.getMessage());
            return ResponseEntity.status(ex.httpStatus()).body(
                    PoDocumentUploadResponse.failure(poNumber, null, ex.errorCode(), ex.getMessage(), correlationId));
        }
    }

    /** Spring's {*subPath} catch-all capture includes the leading "/" (e.g. "/A1/A2") - drop it for a clean value. */
    private String stripLeadingSlash(String subPath) {
        return (subPath != null && subPath.startsWith("/")) ? subPath.substring(1) : subPath;
    }
}
