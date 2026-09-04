package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;

import java.util.List;

/**
 * Retrieves and stores the eligible documents for a PO. Implementations
 * must never return a partial list on download failure - they should throw
 * instead so no partial envelope is ever created.
 */
public interface SharePointDocumentService {

    /**
     * Folder layout: {poNumber}/{revision}. PDF-only - used by DocuSign
     * envelope creation, which needs real PDFs for the anchor-tag signing
     * flow. For the broader set of file types this app can store (see
     * uploadDocument), use fetchAllSupportedDocuments instead.
     */
    List<SharePointDocument> fetchDocuments(String poNumber, String revision);

    /** Flat folder layout: {poNumber} itself contains the documents directly, no revision subfolder. PDF-only, see above. */
    List<SharePointDocument> fetchDocuments(String poNumber);

    /**
     * Same as fetchDocuments(poNumber, revision), but returns every file
     * type this app can also store (PDF, Word, Excel, images - see
     * FileValidationUtil.SUPPORTED_UPLOAD_EXTENSIONS) rather than PDF-only.
     * Used by the documents-only read endpoint (GET /api/v1/po-documents/...),
     * not by DocuSign envelope creation.
     */
    List<SharePointDocument> fetchAllSupportedDocuments(String poNumber, String revision);

    /** Flat folder layout counterpart to fetchAllSupportedDocuments(poNumber, revision). */
    List<SharePointDocument> fetchAllSupportedDocuments(String poNumber);

    /**
     * Stores one already-validated document into the {poNumber}/{revision}
     * folder, creating the folder first if it doesn't exist yet.
     */
    SharePointUploadResult uploadDocument(String poNumber, String revision, String fileName, byte[] content, String contentType);

    /** Flat folder layout: {poNumber} itself, no revision subfolder. */
    SharePointUploadResult uploadDocument(String poNumber, String fileName, byte[] content, String contentType);
}
