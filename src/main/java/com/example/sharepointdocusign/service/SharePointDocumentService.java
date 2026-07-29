package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.model.SharePointDocument;

import java.util.List;

/**
 * Retrieves the eligible PDF documents for a PO. Implementations must never
 * return a partial list on download failure - they should throw instead so
 * no partial envelope is ever created.
 */
public interface SharePointDocumentService {

    /** Folder layout: {poNumber}/REV-{revision}. */
    List<SharePointDocument> fetchDocuments(String poNumber, String revision);

    /** Flat folder layout: {poNumber} itself contains the documents directly, no revision subfolder. */
    List<SharePointDocument> fetchDocuments(String poNumber);
}
