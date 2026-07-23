package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.model.SharePointDocument;

import java.util.List;

/**
 * Retrieves the eligible PDF documents for a PO number / revision.
 * Implementations must never return a partial list on download failure -
 * they should throw instead so no partial envelope is ever created.
 */
public interface SharePointDocumentService {

    List<SharePointDocument> fetchDocuments(String poNumber, String revision);
}
