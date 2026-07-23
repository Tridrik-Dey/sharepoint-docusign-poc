package com.example.sharepointdocusign.controller;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against the real REST endpoint with the mock SharePoint
 * and DocuSign services active - no external services are called.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"mock", "test"})
class PoEnvelopeControllerIntegrationTest {

    private static final byte[] VALID_PDF = "%PDF-1.4\nsample\n%%EOF".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsEnvelopeSuccessfullyInMockMode() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata", "application/json", ("""
                {"poNumber":"4500000105","revision":"02","vendorName":"Dummy Vendor SRL","vendorEmail":"test@example.com"}
                """).getBytes());
        MockMultipartFile mainDoc = new MockMultipartFile(
                "purchaseOrderDocument", "Purchase-Order-4500000105.pdf", "application/pdf", VALID_PDF);

        mockMvc.perform(multipart("/api/v1/po-envelopes").file(metadata).file(mainDoc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("4500000105"))
                .andExpect(jsonPath("$.revision").value("02"))
                .andExpect(jsonPath("$.envelopeId").value(Matchers.startsWith("mock-envelope-")))
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.documentsIncluded", Matchers.hasSize(4)))
                .andExpect(jsonPath("$.documentsIncluded[0]").value("Purchase-Order-4500000105.pdf"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsInvalidPoNumberFormat() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata", "application/json", ("""
                {"poNumber":"invalid/po","revision":"02","vendorName":"Dummy Vendor SRL","vendorEmail":"test@example.com"}
                """).getBytes());
        MockMultipartFile mainDoc = new MockMultipartFile(
                "purchaseOrderDocument", "Purchase-Order.pdf", "application/pdf", VALID_PDF);

        mockMvc.perform(multipart("/api/v1/po-envelopes").file(metadata).file(mainDoc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsNonPdfMainDocument() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata", "application/json", ("""
                {"poNumber":"4500000105","revision":"02","vendorName":"Dummy Vendor SRL","vendorEmail":"test@example.com"}
                """).getBytes());
        MockMultipartFile mainDoc = new MockMultipartFile(
                "purchaseOrderDocument", "Purchase-Order.txt", "text/plain", "not a pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/po-envelopes").file(metadata).file(mainDoc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_PDF"));
    }

    @Test
    void returnsFolderNotFoundForUnknownPo() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata", "application/json", ("""
                {"poNumber":"0000000000","revision":"01","vendorName":"Dummy Vendor SRL","vendorEmail":"test@example.com"}
                """).getBytes());
        MockMultipartFile mainDoc = new MockMultipartFile(
                "purchaseOrderDocument", "Purchase-Order.pdf", "application/pdf", VALID_PDF);

        mockMvc.perform(multipart("/api/v1/po-envelopes").file(metadata).file(mainDoc))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SHAREPOINT_FOLDER_NOT_FOUND"))
                .andExpect(jsonPath("$.poNumber").value("0000000000"));
    }

    @Test
    void returnsBadRequestWhenMainDocumentPartIsMissing() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata", "application/json", ("""
                {"poNumber":"4500000105","revision":"02","vendorName":"Dummy Vendor SRL","vendorEmail":"test@example.com"}
                """).getBytes());

        mockMvc.perform(multipart("/api/v1/po-envelopes").file(metadata))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
