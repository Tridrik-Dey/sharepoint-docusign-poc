package com.example.sharepointdocusign.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies ApiKeyFilter: once app.security.api-key is configured, /api/**
 * requests are rejected without a matching X-Api-Key header, and allowed
 * through with one. Complements PoEnvelopeControllerIntegrationTest, which
 * runs with no API key configured (the default, permissive local-dev mode).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"mock", "test"})
@TestPropertySource(properties = "app.security.api-key=test-shared-secret")
class ApiKeyFilterIntegrationTest {

    private static final byte[] VALID_PDF = "%PDF-1.4\nsample\n%%EOF".getBytes();

    @Autowired
    private MockMvc mockMvc;

    private MockMultipartFile metadataPart() {
        return new MockMultipartFile("metadata", "metadata", "application/json", ("""
                {"poNumber":"4500000105","revision":"02","vendorName":"Dummy Vendor SRL","vendorEmail":"test@example.com"}
                """).getBytes());
    }

    private MockMultipartFile mainDocumentPart() {
        return new MockMultipartFile("purchaseOrderDocument", "Purchase-Order.pdf", "application/pdf", VALID_PDF);
    }

    @Test
    void rejectsRequestWithoutApiKeyHeader() throws Exception {
        mockMvc.perform(multipart("/api/v1/po-envelopes").file(metadataPart()).file(mainDocumentPart()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsRequestWithWrongApiKeyHeader() throws Exception {
        mockMvc.perform(multipart("/api/v1/po-envelopes")
                        .file(metadataPart()).file(mainDocumentPart())
                        .header(ApiKeyFilter.HEADER_NAME, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void allowsRequestWithCorrectApiKeyHeader() throws Exception {
        mockMvc.perform(multipart("/api/v1/po-envelopes")
                        .file(metadataPart()).file(mainDocumentPart())
                        .header(ApiKeyFilter.HEADER_NAME, "test-shared-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
