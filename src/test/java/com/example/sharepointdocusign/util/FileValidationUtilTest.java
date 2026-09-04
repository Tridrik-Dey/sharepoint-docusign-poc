package com.example.sharepointdocusign.util;

import com.example.sharepointdocusign.exception.UnsupportedDocumentException;
import com.example.sharepointdocusign.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationUtilTest {

    private static final byte[] VALID_PDF_BYTES = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Test
    void detectsPdfMagicBytes() {
        assertThat(FileValidationUtil.hasPdfMagicBytes(VALID_PDF_BYTES)).isTrue();
        assertThat(FileValidationUtil.hasPdfMagicBytes("not a pdf".getBytes())).isFalse();
        assertThat(FileValidationUtil.hasPdfMagicBytes(new byte[]{1, 2})).isFalse();
        assertThat(FileValidationUtil.hasPdfMagicBytes(null)).isFalse();
    }

    @Test
    void validatePdfAcceptsValidPdf() {
        assertThatCode(() -> FileValidationUtil.validatePdf(VALID_PDF_BYTES, "application/pdf", "doc.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePdfRejectsNonPdfContentType() {
        assertThatThrownBy(() -> FileValidationUtil.validatePdf(VALID_PDF_BYTES, "image/png", "doc.pdf"))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void validatePdfRejectsMissingMagicBytesEvenWithPdfContentType() {
        byte[] fakeContent = "This is not really a PDF even though the content type claims it is."
                .getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> FileValidationUtil.validatePdf(fakeContent, "application/pdf", "doc.pdf"))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void validatePdfRejectsEmptyContent() {
        assertThatThrownBy(() -> FileValidationUtil.validatePdf(new byte[0], "application/pdf", "doc.pdf"))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void sanitizeFilenameStripsDirectoryComponents() {
        assertThat(FileValidationUtil.sanitizeFilename("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(FileValidationUtil.sanitizeFilename("C:\\temp\\Purchase-Order.pdf")).isEqualTo("Purchase-Order.pdf");
    }

    @Test
    void sanitizeFilenameRejectsBlankOrTraversalOnlyNames() {
        assertThatThrownBy(() -> FileValidationUtil.sanitizeFilename(""))
                .isInstanceOf(UnsupportedDocumentException.class);
        assertThatThrownBy(() -> FileValidationUtil.sanitizeFilename(".."))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void validateSupportedUploadAcceptsAllAllowedExtensions() {
        assertThat(FileValidationUtil.validateSupportedUpload(VALID_PDF_BYTES, "doc.pdf")).isEqualTo("application/pdf");
        assertThat(FileValidationUtil.validateSupportedUpload("word".getBytes(), "doc.doc")).isEqualTo("application/msword");
        assertThat(FileValidationUtil.validateSupportedUpload("word".getBytes(), "doc.docx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(FileValidationUtil.validateSupportedUpload("excel".getBytes(), "sheet.xls")).isEqualTo("application/vnd.ms-excel");
        assertThat(FileValidationUtil.validateSupportedUpload("excel".getBytes(), "sheet.xlsx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        assertThat(FileValidationUtil.validateSupportedUpload(jpeg, "photo.jpg")).isEqualTo("image/jpeg");
        assertThat(FileValidationUtil.validateSupportedUpload(jpeg, "photo.jpeg")).isEqualTo("image/jpeg");

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00};
        assertThat(FileValidationUtil.validateSupportedUpload(png, "photo.png")).isEqualTo("image/png");
    }

    @Test
    void validateSupportedUploadRejectsAnUnknownExtension() {
        assertThatThrownBy(() -> FileValidationUtil.validateSupportedUpload("hello".getBytes(), "notes.txt"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void validateSupportedUploadRejectsPdfExtensionWithWrongMagicBytes() {
        assertThatThrownBy(() -> FileValidationUtil.validateSupportedUpload("not a pdf".getBytes(), "doc.pdf"))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void validateSupportedUploadRejectsJpegExtensionWithWrongMagicBytes() {
        assertThatThrownBy(() -> FileValidationUtil.validateSupportedUpload("not a jpeg".getBytes(), "photo.jpg"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void validateSupportedUploadRejectsPngExtensionWithWrongMagicBytes() {
        assertThatThrownBy(() -> FileValidationUtil.validateSupportedUpload("not a png".getBytes(), "photo.png"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void validateSupportedUploadRejectsEmptyContent() {
        assertThatThrownBy(() -> FileValidationUtil.validateSupportedUpload(new byte[0], "doc.pdf"))
                .isInstanceOf(UnsupportedDocumentException.class);
    }
}
