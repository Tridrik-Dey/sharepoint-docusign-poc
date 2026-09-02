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
 * Proves the DocuSign webhook is wired under the real (non-mock,
 * non-sharepoint-test) profile - same profile guard as DocusignClient.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocusignWebhookWiredUnderRealProfileTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void webhookBeansAreWiredUnderTheRealProfile() {
        assertThat(context.getBean(DocusignWebhookController.class)).isNotNull();
        assertThat(context.getBean(DocusignEnvelopeCompletionService.class)).isNotNull();
    }
}
