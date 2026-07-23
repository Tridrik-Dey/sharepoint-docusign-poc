package com.example.sharepointdocusign.service;

import com.example.sharepointdocusign.client.DocusignClient;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeDefinitionRequest;
import com.example.sharepointdocusign.client.DocusignClient.EnvelopeResponse;
import com.example.sharepointdocusign.client.DocusignClient.TextCustomField;
import com.example.sharepointdocusign.config.ApplicationProperties;
import com.example.sharepointdocusign.model.EnvelopeCreationResult;
import com.example.sharepointdocusign.model.EnvelopeDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocusignEnvelopeServiceImplTest {

    @Mock
    private DocusignClient docusignClient;

    private DocusignEnvelopeServiceImpl service(boolean sendEnvelope) {
        ApplicationProperties properties = new ApplicationProperties(
                new ApplicationProperties.Docusign(sendEnvelope),
                new ApplicationProperties.Documents(10, 10, 25, 20),
                null);
        return new DocusignEnvelopeServiceImpl(docusignClient, properties);
    }

    private List<EnvelopeDocument> sampleDocuments() {
        return List.of(
                new EnvelopeDocument(1, "Purchase-Order-4500000105.pdf", "main-content".getBytes(), "application/pdf", true),
                new EnvelopeDocument(2, "Commercial-Conditions.pdf", "commercial-content".getBytes(), "application/pdf", false),
                new EnvelopeDocument(3, "Technical-Specification.pdf", "technical-content".getBytes(), "application/pdf", false));
    }

    @Test
    void buildsEnvelopeWithAnchorTabOnlyOnMainDocumentAndSapCustomFields() {
        when(docusignClient.createEnvelope(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new EnvelopeResponse("envelope-123", "sent", null, null));

        EnvelopeCreationResult result = service(true).createAndSendEnvelope(
                "4500000105", "02", "Dummy Vendor SRL", "test@example.com", sampleDocuments());

        assertThat(result.envelopeId()).isEqualTo("envelope-123");
        assertThat(result.status()).isEqualTo("sent");
        assertThat(result.documentsIncluded()).containsExactly(
                "Purchase-Order-4500000105.pdf", "Commercial-Conditions.pdf", "Technical-Specification.pdf");

        ArgumentCaptor<EnvelopeDefinitionRequest> captor = ArgumentCaptor.forClass(EnvelopeDefinitionRequest.class);
        verify(docusignClient).createEnvelope(captor.capture());
        EnvelopeDefinitionRequest sentDefinition = captor.getValue();

        assertThat(sentDefinition.emailSubject()).isEqualTo("PO 4500000105 Revision 02 - Signature Request");
        assertThat(sentDefinition.emailBlurb()).isEqualTo("Please review and sign Purchase Order 4500000105, revision 02.");
        assertThat(sentDefinition.status()).isEqualTo("sent");

        assertThat(sentDefinition.documents()).hasSize(3);
        assertThat(sentDefinition.documents().get(0).documentId()).isEqualTo("1");
        assertThat(sentDefinition.documents().get(0).documentBase64())
                .isEqualTo(Base64.getEncoder().encodeToString("main-content".getBytes()));

        var signer = sentDefinition.recipients().signers().get(0);
        assertThat(signer.email()).isEqualTo("test@example.com");
        assertThat(signer.name()).isEqualTo("Dummy Vendor SRL");
        assertThat(signer.tabs().signHereTabs()).hasSize(1);
        assertThat(signer.tabs().signHereTabs().get(0).documentId()).isEqualTo("1");
        assertThat(signer.tabs().signHereTabs().get(0).anchorString()).isEqualTo("/vendor-signature/");

        List<TextCustomField> customFields = sentDefinition.customFields().textCustomFields();
        assertThat(customFields).extracting(TextCustomField::name)
                .containsExactlyInAnyOrder("SAP_PO_NUMBER", "SAP_PO_REVISION", "SHAREPOINT_FOLDER_PATH");
        assertThat(customFields).filteredOn(f -> f.name().equals("SHAREPOINT_FOLDER_PATH"))
                .extracting(TextCustomField::value)
                .containsExactly("4500000105/REV-02");
    }

    @Test
    void createsDraftInsteadOfSendingWhenSendEnvelopeDisabled() {
        when(docusignClient.createEnvelope(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new EnvelopeResponse("envelope-draft", "created", null, null));

        EnvelopeCreationResult result = service(false).createAndSendEnvelope(
                "4500000105", "02", "Dummy Vendor SRL", "test@example.com", sampleDocuments());

        assertThat(result.status()).isEqualTo("created");

        ArgumentCaptor<EnvelopeDefinitionRequest> captor = ArgumentCaptor.forClass(EnvelopeDefinitionRequest.class);
        verify(docusignClient).createEnvelope(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("created");
    }
}
