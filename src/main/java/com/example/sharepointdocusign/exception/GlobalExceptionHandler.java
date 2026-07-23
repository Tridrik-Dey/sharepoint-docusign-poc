package com.example.sharepointdocusign.exception;

import com.example.sharepointdocusign.config.CorrelationIdFilter;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeMetadata;
import com.example.sharepointdocusign.dto.CreatePoEnvelopeResponse;
import com.example.sharepointdocusign.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

/**
 * Central mapping from internal exceptions to the public API error contract.
 * Never forwards raw Microsoft Graph / DocuSign response bodies to callers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PoEnvelopeException.class)
    public ResponseEntity<CreatePoEnvelopeResponse> handlePoEnvelopeException(PoEnvelopeException ex) {
        log.warn("Request failed with errorCode={} message={}", ex.errorCode(), ex.getMessage());
        return ResponseEntity.status(ex.httpStatus())
                .body(CreatePoEnvelopeResponse.failure(null, null, ex.errorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CreatePoEnvelopeResponse> handleValidation(MethodArgumentNotValidException ex) {
        String poNumber = null;
        String revision = null;
        Object target = ex.getBindingResult().getTarget();
        if (target instanceof CreatePoEnvelopeMetadata metadata) {
            poNumber = metadata.poNumber();
            revision = metadata.revision();
        }
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(CreatePoEnvelopeResponse.failure(poNumber, revision, "INVALID_REQUEST", message, correlationId()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        log.warn("Malformed request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_REQUEST", "The request is malformed or missing required parts.", correlationId()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload rejected, exceeds configured maximum size");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of("DOCUMENT_TOO_LARGE", "Uploaded file exceeds the maximum allowed size.", correlationId()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex) {
        log.warn("Multipart parsing failed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_REQUEST", "The multipart request could not be parsed.", correlationId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected internal error occurred.", correlationId()));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
