package com.example.sharepointdocusign.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void getReturnsNonPdfDocumentsTooUnlikeEnvelopeCreation() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/6000000000/01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.documents", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.documents[0].fileName").value("Amendment.docx"))
                .andExpect(jsonPath("$.documents[0].contentType")
                        .value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(jsonPath("$.documents[1].fileName").value("notes.txt"))
                .andExpect(jsonPath("$.documents[1].contentType").value("text/plain"))
                .andExpect(jsonPath("$.documents[2].fileName").value("Photo.jpg"))
                .andExpect(jsonPath("$.documents[2].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.documents[3].fileName").value("Spec.pdf"));
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

    @Test
    void returnsDocumentsForKnownFlatPoFolder() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/8000000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("8000000000"))
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.documents", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.documents[0].fileName").value("notes.txt"))
                .andExpect(jsonPath("$.documents[1].fileName").value("Valid-Doc-A.pdf"))
                .andExpect(jsonPath("$.documents[1].contentBase64").exists())
                .andExpect(jsonPath("$.documents[2].fileName").value("Valid-Doc-B.pdf"));
    }

    @Test
    void returnsFolderNotFoundForUnknownFlatPo() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/1111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SHAREPOINT_FOLDER_NOT_FOUND"))
                .andExpect(jsonPath("$.poNumber").value("1111111111"))
                .andExpect(jsonPath("$.revision").doesNotExist());
    }

    @Test
    void uploadsDocumentForKnownPoAndRevision() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "New-Doc.pdf", "application/pdf", "%PDF-1.4\nnew\n%%EOF".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("4500000105"))
                .andExpect(jsonPath("$.revision").value("02"))
                .andExpect(jsonPath("$.fileName").value("New-Doc.pdf"))
                .andExpect(jsonPath("$.sha256").exists())
                .andExpect(jsonPath("$.renamed").value(false));
    }

    @Test
    void uploadsDocumentForFlatFolder() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "New-Doc.pdf", "application/pdf", "%PDF-1.4\nnew\n%%EOF".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/8000000000").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("8000000000"))
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.fileName").value("New-Doc.pdf"));
    }

    @Test
    void uploadRejectsUnsupportedFileType() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "archive.zip", "application/zip", "zip content".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void uploadAcceptsATxtFile() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("notes.txt"));
    }

    @Test
    void uploadAcceptsAPptAndPptxFile() throws Exception {
        MockMultipartFile ppt = new MockMultipartFile(
                "document", "Slides.ppt", "application/vnd.ms-powerpoint", "legacy-ppt".getBytes());
        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(ppt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("Slides.ppt"));

        MockMultipartFile pptx = new MockMultipartFile(
                "document", "Slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", "ooxml-pptx".getBytes());
        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(pptx))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("Slides.pptx"));
    }

    @Test
    void uploadRejectsFileNamedPdfButNotActuallyAPdf() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "fake.pdf", "application/pdf", "not really a pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_PDF"));
    }

    @Test
    void uploadAcceptsAJpegImage() throws Exception {
        byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile document = new MockMultipartFile("document", "Photo.jpg", "image/jpeg", jpegBytes);

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("Photo.jpg"));
    }

    @Test
    void uploadAcceptsAWordDocument() throws Exception {
        byte[] docxBytes = "fake docx content".getBytes();
        MockMultipartFile document = new MockMultipartFile(
                "document", "Amendment.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("Amendment.docx"));
    }

    @Test
    void uploadRejectsAFileWithJpgExtensionButWrongMagicBytes() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "fake.jpg", "image/jpeg", "not really a jpeg".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void uploadRejectsOversizedDocument() throws Exception {
        byte[] content = new byte[11 * 1024 * 1024];
        System.arraycopy("%PDF-1.4\n".getBytes(), 0, content, 0, 9);
        MockMultipartFile document = new MockMultipartFile("document", "Big.pdf", "application/pdf", content);

        mockMvc.perform(multipart("/api/v1/po-documents/4500000105/02").file(document))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_TOO_LARGE"));
    }

    /**
     * Regression test for a real SAP integration bug: a caller sending a
     * Content-Type the upload endpoint doesn't accept (anything other than
     * multipart/form-data with a boundary) used to fall through to the
     * generic 500 handler with no actionable message. This must now come
     * back as a clean 400 INVALID_REQUEST instead.
     */
    @Test
    void uploadWithUnsupportedContentTypeReturnsCleanBadRequestNotInternalError() throws Exception {
        mockMvc.perform(post("/api/v1/po-documents/4500000105/02")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("not a multipart body"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    /**
     * Regression test for a real SAP integration bug Giorgio hit: a 3rd
     * folder level (a subfolder of a subfolder) used to fall through to the
     * generic 500 handler because the endpoint only ever declared a single
     * {revision} path variable. Both a 3-level and a much deeper path must
     * now succeed, with the full subPath echoed back verbatim in "revision".
     */
    @Test
    void returnsDocumentsForAThreeLevelSubPath() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/4500000233/A1/A2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("4500000233"))
                .andExpect(jsonPath("$.revision").value("A1/A2"))
                .andExpect(jsonPath("$.documents", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.documents[0].fileName").value("Level-A2-Doc.pdf"));
    }

    @Test
    void returnsDocumentsForAFiveLevelSubPath() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/4500000233/A1/A2/A3/A4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("4500000233"))
                .andExpect(jsonPath("$.revision").value("A1/A2/A3/A4"))
                .andExpect(jsonPath("$.documents", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.documents[0].fileName").value("Level-A4-Doc.pdf"));
    }

    @Test
    void uploadsDocumentForAThreeLevelSubPath() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "New-Doc.pdf", "application/pdf", "%PDF-1.4\nnew\n%%EOF".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/4500000233/A1/A2").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.poNumber").value("4500000233"))
                .andExpect(jsonPath("$.revision").value("A1/A2"))
                .andExpect(jsonPath("$.fileName").value("New-Doc.pdf"));
    }

    @Test
    void uploadsDocumentForAFiveLevelSubPath() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "New-Doc.pdf", "application/pdf", "%PDF-1.4\nnew\n%%EOF".getBytes());

        mockMvc.perform(multipart("/api/v1/po-documents/4500000233/A1/A2/A3/A4").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.revision").value("A1/A2/A3/A4"))
                .andExpect(jsonPath("$.fileName").value("New-Doc.pdf"));
    }

    /** Path traversal within a deeper level must still be rejected, not just at the first level. */
    @Test
    void rejectsPathTraversalWithinADeeperSubPathLevel() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/4500000233/A1/../etc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    /** The existing single-extra-segment ("revision") case must keep routing and behaving exactly as before. */
    @Test
    void stillRoutesTheExistingSingleSegmentRevisionCase() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/4500000105/02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value("02"));
    }

    /** The flat single-poNumber case must not be swallowed by the new {*subPath} catch-all. */
    @Test
    void flatFolderCaseIsNotSwallowedByTheCatchAllRoute() throws Exception {
        mockMvc.perform(get("/api/v1/po-documents/8000000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poNumber").value("8000000000"))
                .andExpect(jsonPath("$.revision").doesNotExist());
    }
}
