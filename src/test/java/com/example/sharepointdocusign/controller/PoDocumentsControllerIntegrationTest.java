package com.example.sharepointdocusign.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for GET /api/v1/po-documents/{poNumber}/{revision} against
 * the mock SharePoint service - no DocuSign involved at all for this endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"mock", "test"})
class PoDocumentsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsDocumentsForKnownPoAndRevision() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/4500000105/02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("4500000105"))
                .andExpect(jsonPath("$.revision").value("02"))
                .andExpect(jsonPath("$.documents", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.documents[0].fileName").value("Commercial-Conditions.pdf"))
                .andExpect(jsonPath("$.documents[0].contentBase64").exists())
                .andExpect(jsonPath("$.documents[0].sha256").exists());
    }

    @Test
    void returnsFolderNotFoundForUnknownPo() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/0000000000/01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SHAREPOINT_FOLDER_NOT_FOUND"))
                .andExpect(jsonPath("$.poNumber").value("0000000000"))
                .andExpect(jsonPath("$.revision").value("01"));
    }
}
