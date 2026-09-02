package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;

import java.util.List;

/**
 * Retrieves and stores the eligible PDF documents for a PO. Implementations
 * must never return a partial list on download failure - they should throw
 * instead so no partial envelope is ever created.
 */
public interface SharePointDocumentService {

    /** Folder layout: {poNumber}/REV-{revision}. */
    List<SharePointDocument> fetchDocuments(String poNumber, String revision);

    /** Flat folder layout: {poNumber} itself contains the documents directly, no revision subfolder. */
    List<SharePointDocument> fetchDocuments(String poNumber);

    /**
     * Stores one already-validated document into the {poNumber}/REV-{revision}
     * folder, creating the folder first if it doesn't exist yet.
     */
    SharePointUploadResult uploadDocument(String poNumber, String revision, String fileName, byte[] content, String contentType);

    /** Flat folder layout: {poNumber} itself, no revision subfolder. */
    SharePointUploadResult uploadDocument(String poNumber, String fileName, byte[] content, String contentType);
}
