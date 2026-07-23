package com.example.sharepointdocusign.util;

import com.example.sharepointdocusign.exception.UnsupportedDocumentException;
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
}
