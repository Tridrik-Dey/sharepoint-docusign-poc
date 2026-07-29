package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.MicrosoftGraphClient;
import com.example.sharepointdocusign.client.MicrosoftGraphClient.GraphDriveItem;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.util.FileValidationUtil;
import com.example.sharepointdocusign.util.HashUtil;
import com.example.sharepointdocusign.util.SharePointPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Real SharePointDocumentService backed by Microsoft Graph, active whenever
 * the "mock" profile is not. Only normal (non-folder), non-temporary,
 * PDF files are retrieved - see isEligible().
 */
@Service
@Profile("!mock")
public class SharePointDocumentServiceImpl implements SharePointDocumentService {

    private static final Logger log = LoggerFactory.getLogger(SharePointDocumentServiceImpl.class);

    private final MicrosoftGraphClient graphClient;

    public SharePointDocumentServiceImpl(MicrosoftGraphClient graphClient) {
        this.graphClient = graphClient;
    }

    @Override
    public List<SharePointDocument> fetchDocuments(String poNumber, String revision) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber, revision);
        log.info("Fetching SharePoint documents from folder path '{}'", folderPath);
        String description = "PO " + poNumber + " and revision " + revision;
        return fetchFromFolder(folderPath, description);
    }

    @Override
    public List<SharePointDocument> fetchDocuments(String poNumber) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber);
        log.info("Fetching SharePoint documents from flat folder path '{}'", folderPath);
        String description = "PO " + poNumber;
        return fetchFromFolder(folderPath, description);
    }

    private List<SharePointDocument> fetchFromFolder(String folderPath, String description) {
        List<GraphDriveItem> children = graphClient.listChildren(folderPath);

        List<GraphDriveItem> eligibleItems = children.stream()
                .filter(this::isEligible)
                .sorted(Comparator.comparing(GraphDriveItem::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (eligibleItems.isEmpty()) {
            throw new EmptySharePointFolderException(
                    "SharePoint folder for " + description + " contains no eligible PDF documents.");
        }

        List<SharePointDocument> documents = eligibleItems.stream().map(this::downloadAndValidate).toList();

        documents.forEach(doc -> log.info(
                "Retrieved SharePoint document name={} size={} sha256={}", doc.fileName(), doc.size(), doc.sha256()));
        return documents;
    }

    /**
     * Only normal files (not folders), not temporary/lock files ("~$..."),
     * and PDFs (by declared MIME type or file extension) are eligible.
     */
    private boolean isEligible(GraphDriveItem item) {
        if (item.isFolder()) {
            return false;
        }
        String name = item.name();
        if (name == null || name.isBlank() || name.startsWith("~$")) {
            return false;
        }
        boolean pdfByMimeType = FileValidationUtil.isPdfContentType(item.mimeType());
        boolean pdfByExtension = name.toLowerCase(Locale.ROOT).endsWith(".pdf");
        return pdfByMimeType || pdfByExtension;
    }

    private SharePointDocument downloadAndValidate(GraphDriveItem item) {
        byte[] content = graphClient.downloadContent(item.id(), item.name());
        String contentType = (item.mimeType() != null) ? item.mimeType() : "application/pdf";
        FileValidationUtil.validatePdf(content, contentType, item.name());
        String sha256 = HashUtil.sha256(content);
        return new SharePointDocument(item.id(), item.name(), contentType, content.length, content, sha256);
    }
}
