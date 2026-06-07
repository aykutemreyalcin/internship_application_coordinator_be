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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentExtractionAgent documentExtractionAgent;

    @Test
    void extractEndpointMapsGeminiResultToCaseFields() throws Exception {
        byte[] pdfContent = SampleApplicationPdf.create();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfContent);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String location = uploadResult.getResponse().getContentAsString();
        String caseId = location.substring(location.indexOf("\"caseId\":\"") + 10);
        caseId = caseId.substring(0, caseId.indexOf('"'));

        when(documentExtractionAgent.extract(any()))
                .thenReturn(new ExtractedApplicationData(
                        "Jan Kowalski",
                        "123456",
                        "Computer Engineering",
                        "Astana Kebab Sp. z o.o.",
                        "Anna Nowak",
                        "supervisor@example.com",
                        "2026-06-01",
                        "2026-11-30"));

        mockMvc.perform(post("/api/cases/{id}/extract", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.studentName").value("Jan Kowalski"))
                .andExpect(jsonPath("$.studentId").value("123456"))
                .andExpect(jsonPath("$.fieldOfStudy").value("Computer Engineering"))
                .andExpect(jsonPath("$.companyName").value("Astana Kebab Sp. z o.o."))
                .andExpect(jsonPath("$.supervisorName").value("Anna Nowak"))
                .andExpect(jsonPath("$.supervisorEmail").value("supervisor@example.com"))
                .andExpect(jsonPath("$.internshipStartDate").value("2026-06-01"))
                .andExpect(jsonPath("$.internshipEndDate").value("2026-11-30"))
                .andExpect(jsonPath("$.documents[0].pageCount").value(1));
    }
}
