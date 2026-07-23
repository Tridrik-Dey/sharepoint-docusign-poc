package com.example.sharepointdocusign.util;

import com.example.sharepointdocusign.exception.InvalidRequestException;
import com.example.sharepointdocusign.exception.UnsupportedDocumentException;

import java.util.Locale;

/**
 * PDF content validation, filename sanitization and path-traversal defenses.
 * PDF validity is never inferred from the file extension alone: both the
 * declared MIME type and the "%PDF" magic bytes are checked.
 */
public final class FileValidationUtil {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final String SAFE_PATH_COMPONENT_REGEX = "^[A-Za-z0-9_-]+$";

    private FileValidationUtil() {
    }

    public static boolean isPdfContentType(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf");
    }

    public static boolean hasPdfMagicBytes(byte[] content) {
        if (content == null || content.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates that content is a non-empty PDF, checking both the declared
     * content type and the actual file magic bytes.
     */
    public static void validatePdf(byte[] content, String contentType, String displayName) {
        if (content == null || content.length == 0) {
            throw new UnsupportedDocumentException(displayName + " is empty.");
        }
        if (!isPdfContentType(contentType)) {
            throw new UnsupportedDocumentException(
                    displayName + " must be a PDF file (unexpected content type: " + contentType + ").");
        }
        if (!hasPdfMagicBytes(content)) {
            throw new UnsupportedDocumentException(displayName + " does not appear to be a valid PDF file.");
        }
    }

    /**
     * Strips any directory component from an uploaded file name and rejects
     * traversal sequences, keeping only a safe character set.
     */
    public static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new UnsupportedDocumentException("Uploaded file name is missing.");
        }
        String normalized = rawFilename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String name = (lastSlash >= 0) ? normalized.substring(lastSlash + 1) : normalized;
        name = name.trim();

        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("..")) {
            throw new UnsupportedDocumentException("Uploaded file name is invalid.");
        }
        return name.replaceAll("[^A-Za-z0-9._\\- ]", "_");
    }

    /**
     * Defense-in-depth check for values used to build a SharePoint folder
     * path (PO number, revision). Rejects raw and percent-encoded traversal
     * sequences even though bean validation already constrains the input.
     */
    public static void assertSafePathComponent(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(fieldName + " is required.");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.contains("..") || value.contains("/") || value.contains("\\")
                || lower.contains("%2e%2e") || lower.contains("%2f") || lower.contains("%5c")) {
            throw new InvalidRequestException(fieldName + " contains illegal path characters.");
        }
        if (!value.matches(SAFE_PATH_COMPONENT_REGEX)) {
            throw new InvalidRequestException(
                    fieldName + " may contain letters, numbers, hyphens and underscores only.");
        }
    }
}
