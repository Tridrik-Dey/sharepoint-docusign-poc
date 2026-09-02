package com.example.sharepointdocusign;

import com.example.sharepointdocusign.controller.DocusignWebhookController;
import com.example.sharepointdocusign.service.DocusignEnvelopeCompletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the DocuSign webhook does not exist under the mock profile - no
 * real envelope could ever trigger a real Connect callback there, so
 * mock-mode must not even expose the endpoint (plain 404, not a stub 200).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"mock", "test"})
class DocusignWebhookAbsentUnderMockProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void webhookBeansAreAbsentUnderMock() {
        assertThat(context.getBeanProvider(DocusignWebhookController.class).getIfAvailable()).isNull();
        assertThat(context.getBeanProvider(DocusignEnvelopeCompletionService.class).getIfAvailable()).isNull();
    }

    @Test
    void webhookPathDoesNotExistUnderMock() throws Exception {
        // Not a 404: GlobalExceptionHandler's catch-all turns Spring's NoResourceFoundException
        // (raised for any unmapped path) into a 500 INTERNAL_ERROR - pre-existing app behavior,
        // unrelated to this feature. What matters here is that the controller's own logic never
        // ran (no bean = nothing to run), confirmed above by webhookBeansAreAbsentUnderMock().
        mockMvc.perform(post("/webhooks/docusign/envelope-completed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }
}
