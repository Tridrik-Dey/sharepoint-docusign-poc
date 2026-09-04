package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.MicrosoftGraphClient;
import com.example.sharepointdocusign.client.MicrosoftGraphClient.GraphDriveItem;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;
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
        return fetchFromFolder(folderPath, description, true);
    }

    @Override
    public List<SharePointDocument> fetchDocuments(String poNumber) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber);
        log.info("Fetching SharePoint documents from flat folder path '{}'", folderPath);
        String description = "PO " + poNumber;
        return fetchFromFolder(folderPath, description, true);
    }

    @Override
    public List<SharePointDocument> fetchAllSupportedDocuments(String poNumber, String subPath) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber, subPath);
        log.info("Fetching all supported SharePoint documents from folder path '{}'", folderPath);
        String description = "PO " + poNumber + " and folder path " + subPath;
        return fetchFromFolder(folderPath, description, false);
    }

    @Override
    public List<SharePointDocument> fetchAllSupportedDocuments(String poNumber) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber);
        log.info("Fetching all supported SharePoint documents from flat folder path '{}'", folderPath);
        String description = "PO " + poNumber;
        return fetchFromFolder(folderPath, description, false);
    }

    private List<SharePointDocument> fetchFromFolder(String folderPath, String description, boolean restrictToPdf) {
        List<GraphDriveItem> children = graphClient.listChildren(folderPath);

        List<GraphDriveItem> eligibleItems = children.stream()
                .filter(item -> isEligible(item, restrictToPdf))
                .sorted(Comparator.comparing(GraphDriveItem::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (eligibleItems.isEmpty()) {
            throw new EmptySharePointFolderException(
                    "SharePoint folder for " + description + " contains no eligible documents.");
        }

        List<SharePointDocument> documents = eligibleItems.stream()
                .map(item -> downloadAndValidate(item, restrictToPdf))
                .toList();

        documents.forEach(doc -> log.info(
                "Retrieved SharePoint document name={} size={} sha256={}", doc.fileName(), doc.size(), doc.sha256()));
        return documents;
    }

    /**
     * Only normal files (not folders) and not temporary/lock files ("~$...")
     * are ever eligible. When restrictToPdf is true (envelope creation),
     * only PDFs (by declared MIME type or extension) qualify; otherwise
     * (documents-only read endpoint) any file type this app can also store
     * via uploadDocument qualifies (see FileValidationUtil.SUPPORTED_UPLOAD_EXTENSIONS).
     */
    private boolean isEligible(GraphDriveItem item, boolean restrictToPdf) {
        if (item.isFolder()) {
            return false;
        }
        String name = item.name();
        if (name == null || name.isBlank() || name.startsWith("~$")) {
            return false;
        }
        if (restrictToPdf) {
            boolean pdfByMimeType = FileValidationUtil.isPdfContentType(item.mimeType());
            boolean pdfByExtension = name.toLowerCase(Locale.ROOT).endsWith(".pdf");
            return pdfByMimeType || pdfByExtension;
        }
        return FileValidationUtil.isSupportedUploadExtension(name);
    }

    private SharePointDocument downloadAndValidate(GraphDriveItem item, boolean restrictToPdf) {
        byte[] content = graphClient.downloadContent(item.id(), item.name());
        String contentType;
        if (restrictToPdf) {
            contentType = (item.mimeType() != null) ? item.mimeType() : "application/pdf";
            FileValidationUtil.validatePdf(content, contentType, item.name());
        } else {
            contentType = FileValidationUtil.validateSupportedUpload(content, item.name());
        }
        String sha256 = HashUtil.sha256(content);
        return new SharePointDocument(item.id(), item.name(), contentType, content.length, content, sha256);
    }

    @Override
    public SharePointUploadResult uploadDocument(String poNumber, String subPath, String fileName, byte[] content, String contentType) {
        return uploadToFolder(SharePointPaths.buildFolderPath(poNumber, subPath), fileName, content, contentType);
    }

    @Override
    public SharePointUploadResult uploadDocument(String poNumber, String fileName, byte[] content, String contentType) {
        return uploadToFolder(SharePointPaths.buildFolderPath(poNumber), fileName, content, contentType);
    }

    private SharePointUploadResult uploadToFolder(String folderPath, String fileName, byte[] content, String contentType) {
        log.info("Uploading SharePoint document folderPath={} fileName={} size={}", folderPath, fileName, content.length);
        GraphDriveItem uploaded = graphClient.uploadContent(folderPath, fileName, content, contentType);
        String sha256 = HashUtil.sha256(content);
        boolean renamed = uploaded.name() != null && !uploaded.name().equals(fileName);
        log.info("Uploaded SharePoint document folderPath={} requestedName={} storedName={} size={} sha256={} renamed={}",
                folderPath, fileName, uploaded.name(), content.length, sha256, renamed);
        return new SharePointUploadResult(uploaded.id(), uploaded.name(), content.length, sha256, renamed);
    }
}
