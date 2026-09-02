package com.example.sharepointdocusign.controller;

import com.example.sharepointdocusign.service.DocusignEnvelopeCompletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP-level tests for the DocuSign Connect webhook: HMAC
 * verification and envelopeId extraction, with DocusignEnvelopeCompletionService
 * mocked out so this stays isolated from real DocuSign/SharePoint calls.
 * The secret used here (docusign.connect-hmac-secret) is set in
 * application-test.yml to "test-webhook-secret".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocusignWebhookControllerIntegrationTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String WEBHOOK_PATH = "/webhooks/docusign/envelope-completed";
    private static final String BODY = """
            {"event":"envelope-completed","data":{"envelopeId":"env-webhook-1"}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocusignEnvelopeCompletionService completionService;

    @Test
    void validSignatureIsAcceptedAndTriggersProcessing() throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(DocusignWebhookController.SIGNATURE_HEADER, sign(BODY, SECRET))
                        .content(BODY))
                .andExpect(status().isOk());

        verify(completionService).processEnvelopeCompletion(eq("env-webhook-1"));
    }

    @Test
    void missingSignatureHeaderIsRejected() throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH).content(BODY))
                .andExpect(status().isUnauthorized());

        verify(completionService, never()).processEnvelopeCompletion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signatureComputedWithTheWrongSecretIsRejected() throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(DocusignWebhookController.SIGNATURE_HEADER, sign(BODY, "wrong-secret"))
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(completionService, never()).processEnvelopeCompletion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signatureValidForOriginalBodyIsRejectedAfterBodyIsMutated() throws Exception {
        String tampered = """
                {"event":"envelope-completed","data":{"envelopeId":"env-attacker-controlled"}}""";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(DocusignWebhookController.SIGNATURE_HEADER, sign(BODY, SECRET))
                        .content(tampered))
                .andExpect(status().isUnauthorized());

        verify(completionService, never()).processEnvelopeCompletion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validSignatureButNoExtractableEnvelopeIdIsANoOp() throws Exception {
        String noEnvelopeId = """
                {"event":"envelope-completed","data":{}}""";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(DocusignWebhookController.SIGNATURE_HEADER, sign(noEnvelopeId, SECRET))
                        .content(noEnvelopeId))
                .andExpect(status().isOk());

        verify(completionService, never()).processEnvelopeCompletion(org.mockito.ArgumentMatchers.any());
    }

    /** Computed independently of production code's HMAC path, so a bug there can't cancel itself out. */
    private String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
