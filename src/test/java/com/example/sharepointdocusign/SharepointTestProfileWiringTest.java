package com.example.sharepointdocusign;

import com.example.sharepointdocusign.client.DocusignClient;
import com.example.sharepointdocusign.client.MicrosoftGraphClient;
import com.example.sharepointdocusign.service.DocusignAuthService;
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
 * Proves the "sharepoint-test" profile wires the REAL SharePoint chain
 * (MicrosoftGraphClient -> SharePointDocumentServiceImpl) together with
 * MockDocusignEnvelopeService, and that no real DocuSign bean
 * (DocusignAuthService / DocusignClient / DocusignEnvelopeServiceImpl) is
 * created - so the context loads with zero DocuSign credentials configured.
 */
@SpringBootTest
@ActiveProfiles({"sharepoint-test", "test"})
class SharepointTestProfileWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void realSharePointIsActiveAndDocusignIsMocked() {
        assertThat(context.getBean(SharePointDocumentService.class)).isInstanceOf(SharePointDocumentServiceImpl.class);
        assertThat(context.getBean(DocusignEnvelopeService.class)).isInstanceOf(MockDocusignEnvelopeService.class);

        // Real Graph client must be present - this profile does real Microsoft Graph auth/retrieval.
        assertThat(context.getBeanProvider(MicrosoftGraphClient.class).getIfAvailable()).isNotNull();

        assertThat(context.getBeanProvider(MockSharePointDocumentService.class).getIfAvailable())
                .as("mock SharePoint must not be active under sharepoint-test")
                .isNull();
        assertThat(context.getBeanProvider(DocusignEnvelopeServiceImpl.class).getIfAvailable())
                .as("real DocusignEnvelopeServiceImpl must not be active under sharepoint-test")
                .isNull();
        assertThat(context.getBeanProvider(DocusignAuthService.class).getIfAvailable())
                .as("DocusignAuthService must not be created under sharepoint-test - no DocuSign credentials needed")
                .isNull();
        assertThat(context.getBeanProvider(DocusignClient.class).getIfAvailable())
                .as("DocusignClient must not be created under sharepoint-test - no DocuSign credentials needed")
                .isNull();
    }
}
