package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.model.SharePointDocument;
import com.example.sharepointdocusign.model.SharePointUploadResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockSharePointDocumentServiceTest {

    private final MockSharePointDocumentService service = new MockSharePointDocumentService(
            new ApplicationProperties(
                    new ApplicationProperties.Docusign(true),
                    new ApplicationProperties.Documents(10, 10, 25, 20),
                    new ApplicationProperties.Mock("classpath:mock-sharepoint")));

    @Test
    void returnsOnlyEligiblePdfsSortedAlphabetically() {
        List<SharePointDocument> documents = service.fetchDocuments("7000000000", "01");

        assertThat(documents).extracting(SharePointDocument::fileName)
                .containsExactly("Valid-Doc-A.pdf", "Valid-Doc-B.pdf");
        documents.forEach(doc -> assertThat(doc.sha256()).isNotBlank());
    }

    @Test
    void returnsKnownFixtureDocumentsForMainSampleFolder() {
        List<SharePointDocument> documents = service.fetchDocuments("4500000105", "02");

        assertThat(documents).extracting(SharePointDocument::fileName)
                .containsExactly("Commercial-Conditions.pdf", "Safety-Requirements.pdf", "Technical-Specification.pdf");
    }

    @Test
    void throwsFolderNotFoundForUnknownPoNumber() {
        assertThatThrownBy(() -> service.fetchDocuments("0000000000", "01"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }

    @Test
    void throwsEmptyFolderWhenNoEligiblePdfsPresent() {
        assertThatThrownBy(() -> service.fetchDocuments("9999999999", "01"))
                .isInstanceOf(EmptySharePointFolderException.class);
    }

    @Test
    void returnsOnlyEligiblePdfsForFlatFolderSortedAlphabetically() {
        List<SharePointDocument> documents = service.fetchDocuments("8000000000");

        assertThat(documents).extracting(SharePointDocument::fileName)
                .containsExactly("Valid-Doc-A.pdf", "Valid-Doc-B.pdf");
        documents.forEach(doc -> assertThat(doc.sha256()).isNotBlank());
    }

    @Test
    void throwsFolderNotFoundForUnknownFlatPoNumber() {
        assertThatThrownBy(() -> service.fetchDocuments("1111111111"))
                .isInstanceOf(SharePointFolderNotFoundException.class);
    }

    @Test
    void throwsEmptyFolderWhenNoEligiblePdfsPresentInFlatFolder() {
        assertThatThrownBy(() -> service.fetchDocuments("9999999998"))
                .isInstanceOf(EmptySharePointFolderException.class);
    }

    @Test
    void fetchAllSupportedDocumentsReturnsNonPdfTypesThatFetchDocumentsExcludes() {
        List<SharePointDocument> pdfOnly = service.fetchDocuments("6000000000", "01");
        assertThat(pdfOnly).extracting(SharePointDocument::fileName).containsExactly("Spec.pdf");

        List<SharePointDocument> allSupported = service.fetchAllSupportedDocuments("6000000000", "01");
        assertThat(allSupported).extracting(SharePointDocument::fileName)
                .containsExactly("Amendment.docx", "notes.txt", "Photo.jpg", "Spec.pdf");
        assertThat(allSupported).extracting(SharePointDocument::contentType).containsExactlyInAnyOrder(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain",
                "image/jpeg", "application/pdf");
    }

    @Test
    void simulatesUploadWithoutTouchingTheClasspath() {
        byte[] content = "%PDF-1.4\nnew-doc\n%%EOF".getBytes();

        SharePointUploadResult result = service.uploadDocument("4500000105", "02", "New-Doc.pdf", content, "application/pdf");

        assertThat(result.fileName()).isEqualTo("New-Doc.pdf");
        assertThat(result.size()).isEqualTo(content.length);
        assertThat(result.sha256()).isNotBlank();
        assertThat(result.renamed()).isFalse();
        // Confirms nothing was actually persisted: fetching the same folder still only returns the real fixtures.
        assertThat(service.fetchDocuments("4500000105", "02"))
                .extracting(SharePointDocument::fileName)
                .doesNotContain("New-Doc.pdf");
    }

    @Test
    void simulatesUploadForFlatFolder() {
        byte[] content = "%PDF-1.4\nflat-doc\n%%EOF".getBytes();

        SharePointUploadResult result = service.uploadDocument("8000000000", "New-Doc.pdf", content, "application/pdf");

        assertThat(result.fileName()).isEqualTo("New-Doc.pdf");
        assertThat(result.renamed()).isFalse();
    }

    @Test
    void fetchAllSupportedDocumentsResolvesAMultiLevelSubPath() {
        List<SharePointDocument> documents = service.fetchAllSupportedDocuments("4500000233", "A1/A2");
        assertThat(documents).extracting(SharePointDocument::fileName).containsExactly("Level-A2-Doc.pdf");

        List<SharePointDocument> deeper = service.fetchAllSupportedDocuments("4500000233", "A1/A2/A3/A4");
        assertThat(deeper).extracting(SharePointDocument::fileName).containsExactly("Level-A4-Doc.pdf");
    }

    @Test
    void simulatesUploadForAMultiLevelSubPathWithoutTouchingTheClasspath() {
        byte[] content = "%PDF-1.4\nnested-doc\n%%EOF".getBytes();

        SharePointUploadResult result = service.uploadDocument("4500000233", "A1/A2/A3/A4", "New-Doc.pdf", content, "application/pdf");

        assertThat(result.fileName()).isEqualTo("New-Doc.pdf");
        assertThat(result.renamed()).isFalse();
        // Confirms nothing was actually persisted: fetching the same nested folder still only returns the real fixture.
        assertThat(service.fetchAllSupportedDocuments("4500000233", "A1/A2/A3/A4"))
                .extracting(SharePointDocument::fileName)
                .doesNotContain("New-Doc.pdf");
    }
}
