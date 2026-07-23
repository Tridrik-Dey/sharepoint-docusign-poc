package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.model.SharePointDocument;
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

/**
 * Test-double for SharePointDocumentService, active on the "mock" profile.
 * Reads PDFs from src/main/resources/mock-sharepoint/{poNumber}/REV-{revision}/
 * instead of calling Microsoft Graph.
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

        Resource[] anyFilesInRevisionFolder = list(folderPath + "/*");
        if (anyFilesInRevisionFolder.length == 0) {
            Resource[] anyFilesUnderPoNumber = list(poNumber + "/**");
            if (anyFilesUnderPoNumber.length == 0) {
                throw new SharePointFolderNotFoundException(
                        "No SharePoint folder was found for PO " + poNumber + " and revision " + revision + ".");
            }
            throw new EmptySharePointFolderException(
                    "SharePoint folder for PO " + poNumber + " and revision " + revision + " contains no eligible documents.");
        }

        List<SharePointDocument> documents = Arrays.stream(anyFilesInRevisionFolder)
                .filter(Resource::isReadable)
                .filter(resource -> isEligiblePdf(resource.getFilename()))
                .map(this::toSharePointDocument)
                .sorted(Comparator.comparing(SharePointDocument::fileName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (documents.isEmpty()) {
            throw new EmptySharePointFolderException(
                    "SharePoint folder for PO " + poNumber + " and revision " + revision
                            + " contains no eligible PDF documents.");
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

    private boolean isEligiblePdf(String filename) {
        if (filename == null || filename.isBlank() || filename.startsWith("~$")) {
            return false;
        }
        return filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private SharePointDocument toSharePointDocument(Resource resource) {
        try {
            byte[] content = resource.getContentAsByteArray();
            String filename = resource.getFilename();
            FileValidationUtil.validatePdf(content, "application/pdf", filename);
            String sha256 = HashUtil.sha256(content);
            return new SharePointDocument("mock-" + filename, filename, "application/pdf", content.length, content, sha256);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read mock SharePoint resource " + resource.getFilename(), e);
        }
    }
}
