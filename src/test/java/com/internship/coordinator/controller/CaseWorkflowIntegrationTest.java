package com.internship.coordinator.controller;

import com.internship.coordinator.agent.ClarificationRequestAgent;
import com.internship.coordinator.agent.DecisionRecommendationAgent;
import com.internship.coordinator.agent.DocumentExtractionAgent;
import com.internship.coordinator.agent.SupervisorVerificationAgent;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.dto.GeneratedClarificationDraft;
import com.internship.coordinator.dto.GeneratedRecommendation;
import com.internship.coordinator.dto.GeneratedSupervisorVerificationDraft;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.repository.AuditLogEntryRepository;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @MockitoBean
    private DocumentExtractionAgent documentExtractionAgent;

    @MockitoBean
    private DecisionRecommendationAgent decisionRecommendationAgent;

    @MockitoBean
    private ClarificationRequestAgent clarificationRequestAgent;

    @MockitoBean
    private SupervisorVerificationAgent supervisorVerificationAgent;

    @Test
    void coordinatorWorkflowFromUploadThroughDecision() throws Exception {
        byte[] pdfContent = SampleApplicationPdf.create();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfContent);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("NEW")))
                .andReturn();

        String caseId = extractCaseId(uploadResult.getResponse().getContentAsString());

        when(documentExtractionAgent.extract(any()))
                .thenReturn(new ExtractedApplicationData(
                        "Jan Kowalski",
                        "123456",
                        "Computer Engineering",
                        "Astana Kebab Sp. z o.o.",
                        "Anna Nowak",
                        "supervisor@example.com",
                        "2026-06-01",
                        "2026-10-28"));

        mockMvc.perform(post("/api/cases/{id}/extract", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName", is("Jan Kowalski")))
                .andExpect(jsonPath("$.validation.completeness.passed", is(true)))
                .andExpect(jsonPath("$.validation.rules.passed", is(true)));

        mockMvc.perform(get("/api/cases/{id}/validation", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness.passed", is(true)))
                .andExpect(jsonPath("$.rules.passed", is(true)));

        when(decisionRecommendationAgent.recommend(any(), any()))
                .thenReturn(new GeneratedRecommendation(
                        Recommendation.APPROVE,
                        "All required fields are present; duration complies with rules."));

        mockMvc.perform(post("/api/cases/{id}/recommendation", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY_FOR_REVIEW")))
                .andExpect(jsonPath("$.recommendation", is("APPROVE")));

        mockMvc.perform(post("/api/cases/{id}/decision", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","note":"Verified with department."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(get("/api/cases/{id}/audit", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));

        var applicationCase = applicationCaseRepository
                .findById(java.util.UUID.fromString(caseId))
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(
                "APPROVED", applicationCase.getStatus().name());
        org.junit.jupiter.api.Assertions.assertTrue(
                auditLogEntryRepository
                                .findByApplicationCaseCaseIdOrderByTimestampAsc(applicationCase.getCaseId())
                                .size()
                        >= 5);

        verify(documentExtractionAgent).extract(any());
        verify(decisionRecommendationAgent).recommend(any(), any());
        verifyNoMoreInteractions(clarificationRequestAgent, supervisorVerificationAgent);
    }

    @Test
    void clarificationAndSupervisorDraftEndpointsUseMockedGeminiAgents() throws Exception {
        var applicationCase = applicationCaseRepository.save(com.internship.coordinator.model.ApplicationCase.builder()
                .status(com.internship.coordinator.model.CaseStatus.READY_FOR_REVIEW)
                .studentName("Jan Kowalski")
                .studentId("123456")
                .fieldOfStudy("Computer Engineering")
                .companyName("Example GmbH")
                .supervisorName("Anna Nowak")
                .supervisorEmail("supervisor@example.com")
                .internshipStartDate(java.time.LocalDate.of(2026, 6, 1))
                .internshipEndDate(java.time.LocalDate.of(2026, 10, 28))
                .build());

        when(clarificationRequestAgent.collectTopics(any(), any()))
                .thenReturn(java.util.List.of("studentId: Student ID is missing"));
        when(clarificationRequestAgent.generateDraft(any(), any(), any()))
                .thenReturn(new GeneratedClarificationDraft(
                        "Clarification required",
                        "Dear Jan Kowalski, please provide your student ID."));

        mockMvc.perform(post("/api/cases/{id}/clarification", applicationCase.getCaseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject", is("Clarification required")))
                .andExpect(jsonPath("$.status", is("CLARIFICATION_REQUESTED")));

        applicationCase.setStatus(com.internship.coordinator.model.CaseStatus.READY_FOR_REVIEW);
        applicationCaseRepository.save(applicationCase);

        when(supervisorVerificationAgent.generateDraft(any(), any()))
                .thenReturn(new GeneratedSupervisorVerificationDraft(
                        "Supervisor verification",
                        "Dear Anna Nowak, please confirm the internship."));

        mockMvc.perform(post("/api/cases/{id}/supervisor-verification", applicationCase.getCaseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject", is("Supervisor verification")))
                .andExpect(jsonPath("$.status", is("PENDING_SUPERVISOR")));

        verify(clarificationRequestAgent).collectTopics(any(), any());
        verify(clarificationRequestAgent).generateDraft(any(), any(), any());
        verify(supervisorVerificationAgent).generateDraft(any(), any());
    }

    private String extractCaseId(String responseBody) {
        String marker = "\"caseId\":\"";
        int start = responseBody.indexOf(marker) + marker.length();
        return responseBody.substring(start, responseBody.indexOf('"', start));
    }
}
