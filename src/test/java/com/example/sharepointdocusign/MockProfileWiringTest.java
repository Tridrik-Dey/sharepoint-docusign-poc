package com.example.sharepointdocusign;

import com.example.sharepointdocusign.service.DocusignEnvelopeService;
import com.example.sharepointdocusign.service.DocusignEnvelopeServiceImpl;
import com.example.sharepointdocusign.service.MockDocusignEnvelopeService;
import com.example.sharepointdocusign.service.MockSharePointDocumentService;
import com.example.sharepointdocusign.service.SharePointDocumentService;
import com.example.sharepointdocusign.service.SharePointDocumentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "mock" profile wires MockSharePointDocumentService and
 * MockDocusignEnvelopeService, and that neither real implementation is
 * present in the context (no accidental ambiguous-bean situation, and no
 * real Graph/DocuSign beans sitting around unused).
 */
@SpringBootTest
@ActiveProfiles({"mock", "test"})
class MockProfileWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void mockSharePointAndMockDocusignAreTheOnlyActiveImplementations() {
        assertThat(context.getBean(SharePointDocumentService.class)).isInstanceOf(MockSharePointDocumentService.class);
        assertThat(context.getBean(DocusignEnvelopeService.class)).isInstanceOf(MockDocusignEnvelopeService.class);

        assertThat(context.getBeanProvider(SharePointDocumentServiceImpl.class).getIfAvailable())
                .as("real SharePointDocumentServiceImpl must not be active under the mock profile")
                .isNull();
        assertThat(context.getBeanProvider(DocusignEnvelopeServiceImpl.class).getIfAvailable())
                .as("real DocusignEnvelopeServiceImpl must not be active under the mock profile")
                .isNull();
    }
}
