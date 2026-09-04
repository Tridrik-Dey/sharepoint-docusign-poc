package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import com.example.sharepointdocusign.util.FileValidationUtil;
import com.example.sharepointdocusign.util.HashUtil;
import com.example.sharepointdocusign.util.SharePointPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Test-double for SharePointDocumentService, active on the "mock" profile.
 * Reads PDFs from src/main/resources/mock-sharepoint/... instead of calling
 * Microsoft Graph - supports both the {poNumber}/REV-{revision} layout and
 * the flat {poNumber}-only layout.
 */
@Service
@Profile("mock")
public class MockSharePointDocumentService implements SharePointDocumentService {

    private static final Logger log = LoggerFactory.getLogger(MockSharePointDocumentService.class);
    private static final String DEFAULT_ROOT = "classpath:mock-sharepoint";

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final String mockRoot;

    public MockSharePointDocumentService(ApplicationProperties applicationProperties) {
        String configuredRoot = (applicationProperties.mock() != null) ? applicationProperties.mock().sharepointRoot() : null;
        this.mockRoot = (configuredRoot != null && !configuredRoot.isBlank()) ? configuredRoot : DEFAULT_ROOT;
    }

    @Override
    public List<SharePointDocument> fetchDocuments(String poNumber, String revision) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber, revision);
        log.info("[MOCK] Fetching SharePoint documents from folder path '{}'", folderPath);
        String description = "PO " + poNumber + " and revision " + revision;
        return fetchFromFolder(folderPath, poNumber, description, true);
    }

    @Override
    public List<SharePointDocument> fetchDocuments(String poNumber) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber);
        log.info("[MOCK] Fetching SharePoint documents from flat folder path '{}'", folderPath);
        String description = "PO " + poNumber;
        return fetchFromFolder(folderPath, poNumber, description, true);
    }

    @Override
    public List<SharePointDocument> fetchAllSupportedDocuments(String poNumber, String revision) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber, revision);
        log.info("[MOCK] Fetching all supported SharePoint documents from folder path '{}'", folderPath);
        String description = "PO " + poNumber + " and revision " + revision;
        return fetchFromFolder(folderPath, poNumber, description, false);
    }

    @Override
    public List<SharePointDocument> fetchAllSupportedDocuments(String poNumber) {
        String folderPath = SharePointPaths.buildFolderPath(poNumber);
        log.info("[MOCK] Fetching all supported SharePoint documents from flat folder path '{}'", folderPath);
        String description = "PO " + poNumber;
        return fetchFromFolder(folderPath, poNumber, description, false);
    }

    private List<SharePointDocument> fetchFromFolder(
            String folderPath, String poNumber, String description, boolean restrictToPdf) {
        Resource[] anyFilesInFolder = list(folderPath + "/*");
        if (anyFilesInFolder.length == 0) {
            Resource[] anyFilesUnderPoNumber = list(poNumber + "/**");
            if (anyFilesUnderPoNumber.length == 0) {
                throw new SharePointFolderNotFoundException("No SharePoint folder was found for " + description + ".");
            }
            throw new EmptySharePointFolderException(
                    "SharePoint folder for " + description + " contains no eligible documents.");
        }

        List<SharePointDocument> documents = Arrays.stream(anyFilesInFolder)
                .filter(Resource::isReadable)
                .filter(resource -> isEligible(resource.getFilename(), restrictToPdf))
                .map(resource -> toSharePointDocument(resource, restrictToPdf))
                .sorted(Comparator.comparing(SharePointDocument::fileName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (documents.isEmpty()) {
            throw new EmptySharePointFolderException(
                    "SharePoint folder for " + description + " contains no eligible documents.");
        }

        documents.forEach(doc -> log.info(
                "[MOCK] Retrieved SharePoint document name={} size={} sha256={}", doc.fileName(), doc.size(), doc.sha256()));
        return documents;
    }

    private Resource[] list(String relativePattern) {
        try {
            return resourceResolver.getResources(mockRoot + "/" + relativePattern);
        } catch (FileNotFoundException e) {
            // The directory prefix of the pattern does not exist on the classpath - treat as "no matches"
            // rather than a failure, so callers can distinguish "not found" from "empty" folders.
            return new Resource[0];
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list mock SharePoint resources at " + relativePattern, e);
        }
    }

    private boolean isEligible(String filename, boolean restrictToPdf) {
        if (filename == null || filename.isBlank() || filename.startsWith("~$")) {
            return false;
        }
        if (restrictToPdf) {
            return filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        }
        return FileValidationUtil.isSupportedUploadExtension(filename);
    }

    private SharePointDocument toSharePointDocument(Resource resource, boolean restrictToPdf) {
        try {
            byte[] content = resource.getContentAsByteArray();
            String filename = resource.getFilename();
            String contentType;
            if (restrictToPdf) {
                FileValidationUtil.validatePdf(content, "application/pdf", filename);
                contentType = "application/pdf";
            } else {
                contentType = FileValidationUtil.validateSupportedUpload(content, filename);
            }
            String sha256 = HashUtil.sha256(content);
            return new SharePointDocument("mock-" + filename, filename, contentType, content.length, content, sha256);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read mock SharePoint resource " + resource.getFilename(), e);
        }
    }

    /**
     * Simulated upload: mock-mode fixtures live on the classpath, which is
     * read-only at runtime (especially from a packaged jar), so this never
     * writes a real file anywhere - it only fakes a plausible successful
     * result, the same way MockDocusignEnvelopeService fakes an envelope id
     * without contacting DocuSign. {@code renamed} is always false here,
     * since there is no real collision to detect against nothing persisted -
     * SharePoint's actual auto-rename-on-collision behavior can only be
     * observed against real SharePoint (sharepoint-test or local profile).
     */
    @Override
    public SharePointUploadResult uploadDocument(String poNumber, String revision, String fileName, byte[] content, String contentType) {
        return simulateUpload(SharePointPaths.buildFolderPath(poNumber, revision), fileName, content);
    }

    @Override
    public SharePointUploadResult uploadDocument(String poNumber, String fileName, byte[] content, String contentType) {
        return simulateUpload(SharePointPaths.buildFolderPath(poNumber), fileName, content);
    }

    private SharePointUploadResult simulateUpload(String folderPath, String fileName, byte[] content) {
        String sha256 = HashUtil.sha256(content);
        String itemId = "mock-upload-" + UUID.randomUUID();
        log.info("[MOCK] Simulated SharePoint upload folderPath={} fileName={} size={} sha256={}",
                folderPath, fileName, content.length, sha256);
        return new SharePointUploadResult(itemId, fileName, content.length, sha256, false);
    }
}
