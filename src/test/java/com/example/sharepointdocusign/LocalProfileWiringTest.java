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
 * Proves the "local" profile (real SharePoint + real DocuSign) wires both
 * real implementations and neither mock. Bean creation for both chains only
 * stores configuration/WebClient references (private key loading and actual
 * network calls happen lazily on first use), so this context loads cleanly
 * even without live Microsoft/DocuSign credentials configured - i.e. the
 * real beans are "available" as soon as this profile is active, regardless
 * of whether the configured credentials actually work end-to-end.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class LocalProfileWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void realSharePointAndRealDocusignAreBothActiveIfAvailable() {
        assertThat(context.getBean(SharePointDocumentService.class)).isInstanceOf(SharePointDocumentServiceImpl.class);
        assertThat(context.getBean(DocusignEnvelopeService.class)).isInstanceOf(DocusignEnvelopeServiceImpl.class);

        assertThat(context.getBeanProvider(MockSharePointDocumentService.class).getIfAvailable())
                .as("mock SharePoint must not be active under local")
                .isNull();
        assertThat(context.getBeanProvider(MockDocusignEnvelopeService.class).getIfAvailable())
                .as("mock DocuSign must not be active under local")
                .isNull();
    }
}
