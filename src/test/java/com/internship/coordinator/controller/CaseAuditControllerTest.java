package com.internship.coordinator.controller;

import com.internship.coordinator.agent.DecisionRecommendationAgent;
import com.internship.coordinator.agent.DocumentExtractionAgent;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.dto.GeneratedRecommendation;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.AuditLogEntry;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.repository.AuditLogEntryRepository;
import com.internship.coordinator.service.AuditLogService;
import com.internship.coordinator.support.SampleApplicationPdf;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Autowired
    private AuditLogService auditLogService;

    @MockitoBean
    private DocumentExtractionAgent documentExtractionAgent;

    @MockitoBean
    private DecisionRecommendationAgent decisionRecommendationAgent;

    private UUID caseId;

    @BeforeEach
    void setUp() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.READY_FOR_REVIEW)
                .studentName("Jan Kowalski")
                .recommendation(Recommendation.APPROVE)
                .build();
        caseId = applicationCaseRepository.save(applicationCase).getCaseId();
        auditLogService.record(applicationCase, "SYSTEM", "CASE_CREATED", "Uploaded application.pdf");
        auditLogService.recordStatusChange(
                applicationCase, "Decision Recommendation Agent", CaseStatus.NEW, CaseStatus.READY_FOR_REVIEW);
        applicationCaseRepository.save(applicationCase);
    }

    @Test
    void getAuditLogReturnsEntriesOrderedByTimestamp() throws Exception {
        mockMvc.perform(get("/api/cases/{id}/audit", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].actor", is("SYSTEM")))
                .andExpect(jsonPath("$[0].action", is("CASE_CREATED")))
                .andExpect(jsonPath("$[0].detail", is("Uploaded application.pdf")))
                .andExpect(jsonPath("$[0].timestamp").exists())
                .andExpect(jsonPath("$[1].actor", is("Decision Recommendation Agent")))
                .andExpect(jsonPath("$[1].action", is("STATUS_READY_FOR_REVIEW")))
                .andExpect(jsonPath("$[1].detail", is("NEW -> READY_FOR_REVIEW")))
                .andExpect(jsonPath("$[1].timestamp").exists());
    }

    @Test
    void getAuditLogReturnsNotFoundForMissingCase() throws Exception {
        mockMvc.perform(get("/api/cases/{id}/audit", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullWorkflowRecordsCompleteAuditTimeline() throws Exception {
        byte[] pdfContent = SampleApplicationPdf.create();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfContent);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        String workflowCaseId = responseBody.substring(responseBody.indexOf("\"caseId\":\"") + 10);
        workflowCaseId = workflowCaseId.substring(0, workflowCaseId.indexOf('"'));

        when(documentExtractionAgent.extract(any()))
                .thenReturn(new ExtractedApplicationData(
                        "Jan Kowalski",
                        "123456",
                        "Computer Engineering",
                        "Example GmbH",
                        "Anna Nowak",
                        "supervisor@example.com",
                        "2026-06-01",
                        "2026-10-28"));

        mockMvc.perform(post("/api/cases/{id}/extract", workflowCaseId))
                .andExpect(status().isOk());

        when(decisionRecommendationAgent.recommend(any(), any()))
                .thenReturn(new GeneratedRecommendation(Recommendation.APPROVE, "All checks passed."));

        mockMvc.perform(post("/api/cases/{id}/recommendation", workflowCaseId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{id}/decision", workflowCaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","note":"Confirmed."}
                                """))
                .andExpect(status().isOk());

        UUID parsedCaseId = UUID.fromString(workflowCaseId);
        List<AuditLogEntry> entries =
                auditLogEntryRepository.findByApplicationCaseCaseIdOrderByTimestampAsc(parsedCaseId);

        org.junit.jupiter.api.Assertions.assertTrue(entries.size() >= 8);
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM", entries.getFirst().getActor());
        org.junit.jupiter.api.Assertions.assertEquals("CASE_CREATED", entries.getFirst().getAction());
        org.junit.jupiter.api.Assertions.assertTrue(entries.stream()
                .anyMatch(entry -> "EXTRACTION_COMPLETED".equals(entry.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(entries.stream()
                .anyMatch(entry -> "VALIDATION_COMPLETENESS".equals(entry.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(entries.stream()
                .anyMatch(entry -> "VALIDATION_RULES".equals(entry.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(entries.stream()
                .anyMatch(entry -> "RECOMMENDATION".equals(entry.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(entries.stream()
                .anyMatch(entry -> "DECISION_APPROVE".equals(entry.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(entries.stream()
                .anyMatch(entry -> "STATUS_APPROVED".equals(entry.getAction())));

        mockMvc.perform(get("/api/cases/{id}/audit", workflowCaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(entries.size())))
                .andExpect(jsonPath("$[0].actor", is("SYSTEM")))
                .andExpect(jsonPath("$[0].action", is("CASE_CREATED")))
                .andExpect(jsonPath("$[0].timestamp").exists());
    }
}
