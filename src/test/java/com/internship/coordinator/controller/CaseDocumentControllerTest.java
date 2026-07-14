package com.internship.coordinator.controller;

import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseDocumentControllerTest {

    private static final byte[] PDF_CONTENT = "%PDF-1.4\n% mock pdf content".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @Test
    void uploadAndDownloadPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, PDF_CONTENT);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].fileName", is("application.pdf")))
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        UUID caseId = UUID.fromString(JsonPath.read(responseBody, "$.caseId"));
        UUID documentId = UUID.fromString(JsonPath.read(responseBody, "$.documents[0].id"));

        mockMvc.perform(get("/api/cases/{caseId}/documents/{documentId}", caseId, documentId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"application.pdf\""))
                .andExpect(result -> assertArrayEquals(PDF_CONTENT, result.getResponse().getContentAsByteArray()));

        assertTrue(applicationCaseRepository.findById(caseId).isPresent());
    }

    @Test
    void uploadRejectsNonPdfContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.TEXT_PLAIN_VALUE, PDF_CONTENT);

        mockMvc.perform(multipart("/api/cases").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void uploadRejectsInvalidPdfSignature() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "application.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "not-a-pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/cases").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void uploadRejectsOversizedPdf() throws Exception {
        byte[] oversizedContent = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF".getBytes(StandardCharsets.US_ASCII), 0, oversizedContent, 0, 4);

        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, oversizedContent);

        mockMvc.perform(multipart("/api/cases").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void downloadReturnsNotFoundForMissingDocument() throws Exception {
        mockMvc.perform(get("/api/cases/{caseId}/documents/{documentId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
