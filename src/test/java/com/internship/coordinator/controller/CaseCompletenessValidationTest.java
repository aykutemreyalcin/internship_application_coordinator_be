package com.internship.coordinator.controller;

import com.internship.coordinator.agent.DocumentExtractionAgent;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.support.SampleApplicationPdf;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseCompletenessValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentExtractionAgent documentExtractionAgent;

    @Test
    void extractStoresCompletenessIssuesForMissingFields() throws Exception {
        byte[] pdfContent = SampleApplicationPdf.create();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfContent);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String body = uploadResult.getResponse().getContentAsString();
        String caseId = body.substring(body.indexOf("\"caseId\":\"") + 10);
        caseId = caseId.substring(0, caseId.indexOf('"'));

        when(documentExtractionAgent.extract(any()))
                .thenReturn(new ExtractedApplicationData(
                        "Jan Kowalski",
                        null,
                        "Computer Engineering",
                        "Astana Kebab Sp. z o.o.",
                        null,
                        null,
                        "2026-06-01",
                        null));

        mockMvc.perform(post("/api/cases/{id}/extract", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.completeness.passed", is(false)))
                .andExpect(jsonPath("$.validation.completeness.issues", hasSize(4)))
                .andExpect(jsonPath("$.validation.completeness.issues[*].field", containsInAnyOrder(
                        "studentId", "supervisorName", "supervisorEmail", "internshipEndDate")))
                .andExpect(jsonPath("$.validation.completeness.issues[*].message", containsInAnyOrder(
                        "Student ID is missing",
                        "Supervisor name is missing",
                        "Supervisor email is missing",
                        "Internship end date is missing")));
    }
}
