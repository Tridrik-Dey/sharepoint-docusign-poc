package com.example.sharepointdocusign;

import com.example.sharepointdocusign.controller.DocusignWebhookController;
import com.example.sharepointdocusign.service.DocusignEnvelopeCompletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the DocuSign webhook does not exist under sharepoint-test either -
 * that profile pairs real SharePoint with mock DocuSign, so DocusignClient
 * itself isn't even wired there (see SharepointTestProfileWiringTest), and
 * the webhook shares that exact same profile guard.
 */
@SpringBootTest
@ActiveProfiles({"sharepoint-test", "test"})
class DocusignWebhookAbsentUnderSharepointTestProfileTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void webhookBeansAreAbsentUnderSharepointTest() {
        assertThat(context.getBeanProvider(DocusignWebhookController.class).getIfAvailable()).isNull();
        assertThat(context.getBeanProvider(DocusignEnvelopeCompletionService.class).getIfAvailable()).isNull();
    }
}
