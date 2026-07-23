package com.example.sharepointdocusign.controller;

import com.example.sharepointdocusign.config.CorrelationIdFilter;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeMetadata;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeResponse;
import com.example.sharepointdocusign.exception.PoEnvelopeException;
import com.example.sharepointdocusign.service.PoEnvelopeOrchestrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PoEnvelopeController {

    private static final Logger log = LoggerFactory.getLogger(PoEnvelopeController.class);

    private final PoEnvelopeOrchestrationService orchestrationService;

    public PoEnvelopeController(PoEnvelopeOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping(path = "/api/v1/po-envelopes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatePoEnvelopeResponse> createPoEnvelope(
            @Valid @RequestPart("metadata") CreatePoEnvelopeMetadata metadata,
            @RequestPart("purchaseOrderDocument") MultipartFile purchaseOrderDocument) {

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.info("Received PO envelope request poNumber={} revision={} correlationId={}",
                metadata.poNumber(), metadata.revision(), correlationId);

        try {
            CreatePoEnvelopeResponse response =
                    orchestrationService.processPoEnvelopeRequest(metadata, purchaseOrderDocument, correlationId);
            return ResponseEntity.ok(response);
        } catch (PoEnvelopeException ex) {
            // Caught here (rather than only in the global handler) so the response can echo back
            // the poNumber/revision from the already-validated request metadata.
            log.warn("Request failed with errorCode={} message={}", ex.errorCode(), ex.getMessage());
            return ResponseEntity.status(ex.httpStatus()).body(CreatePoEnvelopeResponse.failure(
                    metadata.poNumber(), metadata.revision(), ex.errorCode(), ex.getMessage(), correlationId));
        }
    }
}
