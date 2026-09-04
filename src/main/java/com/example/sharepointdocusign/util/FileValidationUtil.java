package com.example.sharepointdocusign.util;

import com.example.sharepointdocusign.exception.InvalidRequestException;
import com.example.sharepointdocusign.exception.UnsupportedDocumentException;
import com.example.sharepointdocusign.exception.UnsupportedFileTypeException;

import java.util.Locale;
import java.util.Map;

/**
 * PDF content validation, filename sanitization and path-traversal defenses.
 * PDF validity is never inferred from the file extension alone: both the
 * declared MIME type and the "%PDF" magic bytes are checked.
 */
public final class FileValidationUtil {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final String SAFE_PATH_COMPONENT_REGEX = "^[A-Za-z0-9_-]+$";

    /**
     * File types accepted by the SAP write-back endpoint (POST
     * /api/v1/po-documents/...), mapped to the Content-Type stored with the
     * document in SharePoint. PDF and image formats are additionally checked
     * against their magic bytes below; DOC/DOCX/XLS/XLSX are not (the old
     * binary Office format and the ZIP-based OOXML format each share magic
     * bytes across several unrelated file types, so extension + declared
     * content type is the practical check for those - a known, documented
     * simplification).
     */
    private static final Map<String, String> SUPPORTED_UPLOAD_EXTENSIONS = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls", "application/vnd.ms-excel",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

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
     * Validates an uploaded document against the SAP write-back endpoint's
     * allowed file types (see SUPPORTED_UPLOAD_EXTENSIONS) and returns the
     * Content-Type it should be stored with in SharePoint. The extension is
     * taken from the already-sanitized file name. PDF/JPEG/PNG are further
     * checked against their magic bytes; other allowed types are not (see
     * the class-level note on SUPPORTED_UPLOAD_EXTENSIONS).
     */
    public static String validateSupportedUpload(byte[] content, String sanitizedFileName) {
        if (content == null || content.length == 0) {
            throw new UnsupportedDocumentException(sanitizedFileName + " is empty.");
        }
        String extension = extractExtension(sanitizedFileName);
        String contentType = SUPPORTED_UPLOAD_EXTENSIONS.get(extension);
        if (contentType == null) {
            throw new UnsupportedFileTypeException(sanitizedFileName + " has an unsupported file type - allowed: "
                    + String.join(", ", SUPPORTED_UPLOAD_EXTENSIONS.keySet().stream().sorted().toList()) + ".");
        }
        if (extension.equals("pdf") && !hasPdfMagicBytes(content)) {
            throw new UnsupportedDocumentException(sanitizedFileName + " does not appear to be a valid PDF file.");
        }
        if ((extension.equals("jpg") || extension.equals("jpeg")) && !hasJpegMagicBytes(content)) {
            throw new UnsupportedFileTypeException(sanitizedFileName + " does not appear to be a valid JPEG file.");
        }
        if (extension.equals("png") && !hasPngMagicBytes(content)) {
            throw new UnsupportedFileTypeException(sanitizedFileName + " does not appear to be a valid PNG file.");
        }
        return contentType;
    }

    /**
     * Extension-only pre-check (no content yet, e.g. filtering a SharePoint
     * folder listing before downloading anything) against the same allowlist
     * validateSupportedUpload() enforces once content is available.
     */
    public static boolean isSupportedUploadExtension(String fileName) {
        return fileName != null && SUPPORTED_UPLOAD_EXTENSIONS.containsKey(extractExtension(fileName));
    }

    public static boolean hasJpegMagicBytes(byte[] content) {
        return startsWith(content, JPEG_MAGIC);
    }

    public static boolean hasPngMagicBytes(byte[] content) {
        return startsWith(content, PNG_MAGIC);
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content == null || content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
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
