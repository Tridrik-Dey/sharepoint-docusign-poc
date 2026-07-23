package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.model.EnvelopeCreationResult;
import com.example.sharepointdocusign.model.EnvelopeDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockDocusignEnvelopeServiceTest {

    private final MockDocusignEnvelopeService service = new MockDocusignEnvelopeService();

    @Test
    void returnsFakeSentEnvelopeWithDocumentNamesInOrder() {
        List<EnvelopeDocument> documents = List.of(
                new EnvelopeDocument(1, "Purchase-Order-4500000105.pdf", new byte[]{'%', 'P', 'D', 'F'}, "application/pdf", true),
                new EnvelopeDocument(2, "Commercial-Conditions.pdf", new byte[]{'%', 'P', 'D', 'F'}, "application/pdf", false),
                new EnvelopeDocument(3, "Technical-Specification.pdf", new byte[]{'%', 'P', 'D', 'F'}, "application/pdf", false));

        EnvelopeCreationResult result = service.createAndSendEnvelope(
                "4500000105", "02", "Dummy Vendor SRL", "test@example.com", documents);

        assertThat(result.envelopeId()).startsWith("mock-envelope-");
        assertThat(result.status()).isEqualTo("sent");
        assertThat(result.documentsIncluded()).containsExactly(
                "Purchase-Order-4500000105.pdf", "Commercial-Conditions.pdf", "Technical-Specification.pdf");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void generatesUniqueEnvelopeIdsPerCall() {
        List<EnvelopeDocument> documents = List.of(
                new EnvelopeDocument(1, "doc.pdf", new byte[]{'%', 'P', 'D', 'F'}, "application/pdf", true));

        String first = service.createAndSendEnvelope("1", "01", "Vendor", "vendor@example.com", documents).envelopeId();
        String second = service.createAndSendEnvelope("1", "01", "Vendor", "vendor@example.com", documents).envelopeId();

        assertThat(first).isNotEqualTo(second);
    }
}
