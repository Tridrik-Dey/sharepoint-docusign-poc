package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.exception.EmptySharePointFolderException;
import com.example.sharepointdocusign.exception.SharePointFolderNotFoundException;
import com.example.sharepointdocusign.model.SharePointDocument;
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
}
