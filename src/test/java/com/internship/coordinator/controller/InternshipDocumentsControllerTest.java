package com.internship.coordinator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.agent.DecisionRecommendationAgent;
import com.internship.coordinator.agent.InternshipJournalExtractionAgent;
import com.internship.coordinator.agent.LearningOutcomesReportExtractionAgent;
import com.internship.coordinator.dto.ExtractedInternshipJournalData;
import com.internship.coordinator.dto.ExtractedLearningOutcomesReportData;
import com.internship.coordinator.dto.GeneratedRecommendation;
import com.internship.coordinator.dto.JournalDayEntry;
import com.internship.coordinator.dto.JournalWeekEntry;
import com.internship.coordinator.dto.LearningOutcomeEntry;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.CaseType;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.service.DocumentFileValidator;
import com.internship.coordinator.support.SampleDocx;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InternshipDocumentsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LearningOutcomesReportExtractionAgent learningOutcomesReportExtractionAgent;

    @MockitoBean
    private InternshipJournalExtractionAgent internshipJournalExtractionAgent;

    @MockitoBean
    private DecisionRecommendationAgent decisionRecommendationAgent;

    @Test
    void uploadReportDocxAndExtract() throws Exception {
        byte[] docx = SampleDocx.create("Learning Outcomes Report", "Student: Jan Kowalski");
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.docx", DocumentFileValidator.DOCX_CONTENT_TYPE, docx);

        String caseId = mockMvc.perform(multipart("/api/cases")
                        .file(file)
                        .param("caseType", CaseType.LEARNING_OUTCOMES_REPORT.name()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseType").value("LEARNING_OUTCOMES_REPORT"))
                .andExpect(jsonPath("$.documents[0].contentType").value(DocumentFileValidator.DOCX_CONTENT_TYPE))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(new ObjectMapper().readTree(caseId).get("caseId").asText());

        when(learningOutcomesReportExtractionAgent.extractFromDocx(any(byte[].class)))
                .thenReturn(new ExtractedLearningOutcomesReportData(
                        "Jan Kowalski",
                        "123456",
                        "2026-11-30",
                        "Example Corp",
                        "2026-06-01",
                        "2026-11-30",
                        List.of(new LearningOutcomeEntry(
                                "W1",
                                "Company activities",
                                "Observed daily engineering workflows and participated in code reviews")),
                        true,
                        "Anna Nowak",
                        null,
                        true,
                        "2026-11-29",
                        null,
                        null,
                        null,
                        null));

        mockMvc.perform(post("/api/cases/{id}/extract", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Jan Kowalski"))
                .andExpect(jsonPath("$.extractedPayload.learningOutcomes[0].code").value("W1"));
    }

    @Test
    void uploadJournalDocxAndListByCaseType() throws Exception {
        byte[] docx = SampleDocx.create("Student Internship Journal", "Faculty: Applied Sciences");
        MockMultipartFile file = new MockMultipartFile(
                "file", "journal.docx", DocumentFileValidator.DOCX_CONTENT_TYPE, docx);

        mockMvc.perform(multipart("/api/cases")
                        .file(file)
                        .param("caseType", CaseType.INTERNSHIP_JOURNAL.name()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseType").value("INTERNSHIP_JOURNAL"));

        mockMvc.perform(get("/api/cases").param("caseType", CaseType.INTERNSHIP_JOURNAL.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].caseType").value("INTERNSHIP_JOURNAL"));
    }

    @Test
    void clarificationIsBlockedForDocumentCases() throws Exception {
        byte[] docx = SampleDocx.create("Report");
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.docx", DocumentFileValidator.DOCX_CONTENT_TYPE, docx);

        String response = mockMvc.perform(multipart("/api/cases")
                        .file(file)
                        .param("caseType", CaseType.LEARNING_OUTCOMES_REPORT.name()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(new ObjectMapper().readTree(response).get("caseId").asText());

        mockMvc.perform(post("/api/cases/{id}/clarification", id)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/cases/{id}/supervisor-verification", id)).andExpect(status().isBadRequest());
    }

    @Test
    void journalExtractUsesMockedAgent() throws Exception {
        byte[] docx = SampleDocx.create("Student Internship Journal");
        MockMultipartFile file = new MockMultipartFile(
                "file", "journal.docx", DocumentFileValidator.DOCX_CONTENT_TYPE, docx);

        String response = mockMvc.perform(multipart("/api/cases")
                        .file(file)
                        .param("caseType", CaseType.INTERNSHIP_JOURNAL.name()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(new ObjectMapper().readTree(response).get("caseId").asText());

        when(internshipJournalExtractionAgent.extractFromDocx(any(byte[].class)))
                .thenReturn(new ExtractedInternshipJournalData(
                        "Jan Kowalski",
                        "123456",
                        "Faculty of Applied Sciences",
                        "Computer Engineering",
                        "FULL_TIME",
                        "2025/2026",
                        "Example Corp",
                        "Warsaw",
                        "2026-06-01",
                        "2026-11-30",
                        "Anna Nowak",
                        List.of(new JournalWeekEntry(
                                "2026-06-01",
                                "2026-06-07",
                                List.of(new JournalDayEntry(
                                        "2026-06-01", "10:00", "16:00", 6, "Implemented API endpoints")),
                                true))));

        mockMvc.perform(post("/api/cases/{id}/extract", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedPayload.weeklyEntries[0].weekStart").value("2026-06-01"));
    }

    @Test
    void documentClarifyDecisionSetsClarificationRequestedStatus() throws Exception {
        byte[] docx = SampleDocx.create("Report");
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.docx", DocumentFileValidator.DOCX_CONTENT_TYPE, docx);

        String response = mockMvc.perform(multipart("/api/cases")
                        .file(file)
                        .param("caseType", CaseType.LEARNING_OUTCOMES_REPORT.name()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(new ObjectMapper().readTree(response).get("caseId").asText());

        String decisionBody =
                """
                {"decision":"CLARIFY","note":"Please resubmit with all learning outcomes filled"}
                """;

        mockMvc.perform(post("/api/cases/{id}/decision", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isBadRequest());

        when(learningOutcomesReportExtractionAgent.extractFromDocx(any(byte[].class)))
                .thenReturn(new ExtractedLearningOutcomesReportData(
                        "Jan Kowalski",
                        "123456",
                        "2026-11-30",
                        "Example Corp",
                        "2026-06-01",
                        "2026-11-30",
                        List.of(new LearningOutcomeEntry(
                                "W1",
                                "Company activities",
                                "Observed daily engineering workflows and participated in code reviews")),
                        true,
                        "Anna Nowak",
                        null,
                        true,
                        "2026-11-29",
                        null,
                        null,
                        "YES",
                        null));

        mockMvc.perform(post("/api/cases/{id}/extract", id)).andExpect(status().isOk());

        when(decisionRecommendationAgent.recommend(any(), any(ValidationSummaryDto.class)))
                .thenReturn(new GeneratedRecommendation(Recommendation.CLARIFY, "Missing several learning outcomes"));

        mockMvc.perform(post("/api/cases/{id}/recommendation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));

        mockMvc.perform(post("/api/cases/{id}/decision", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLARIFICATION_REQUESTED"));
    }
}
