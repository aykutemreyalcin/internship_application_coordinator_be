package com.internship.coordinator.controller;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.repository.AuditLogEntryRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseDecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    private UUID caseId;

    @BeforeEach
    void setUp() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.READY_FOR_REVIEW)
                .studentName("Jan Kowalski")
                .recommendation(Recommendation.APPROVE)
                .recommendationReason("All checks passed.")
                .build();
        caseId = applicationCaseRepository.save(applicationCase).getCaseId();
    }

    @Test
    void approveDecisionUpdatesStatusAndRecordsAuditEntry() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/decision", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","note":"Verified with department."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        var auditEntries = auditLogEntryRepository.findByApplicationCaseCaseIdOrderByTimestampAsc(caseId);
        org.junit.jupiter.api.Assertions.assertEquals(2, auditEntries.size());
        org.junit.jupiter.api.Assertions.assertEquals("COORDINATOR", auditEntries.get(0).getActor());
        org.junit.jupiter.api.Assertions.assertEquals("DECISION_APPROVE", auditEntries.get(0).getAction());
        org.junit.jupiter.api.Assertions.assertEquals(
                "APPROVE: Verified with department.", auditEntries.get(0).getDetail());
        org.junit.jupiter.api.Assertions.assertEquals("COORDINATOR", auditEntries.get(1).getActor());
        org.junit.jupiter.api.Assertions.assertEquals("STATUS_APPROVED", auditEntries.get(1).getAction());
        org.junit.jupiter.api.Assertions.assertEquals(
                "READY_FOR_REVIEW -> APPROVED", auditEntries.get(1).getDetail());
    }

    @Test
    void rejectDecisionUpdatesStatus() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/decision", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECT","note":"Duration exceeds policy."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));
    }

    @Test
    void clarifyDecisionUpdatesStatusToNeedsClarification() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/decision", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CLARIFY","note":"Need updated supervisor contact."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NEEDS_CLARIFICATION")));
    }

    @Test
    void decisionRejectedFromNewStatus() throws Exception {
        ApplicationCase applicationCase = applicationCaseRepository.findById(caseId).orElseThrow();
        applicationCase.setStatus(CaseStatus.NEW);
        applicationCaseRepository.save(applicationCase);

        mockMvc.perform(post("/api/cases/{id}/decision", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void decisionRejectedFromApprovedStatus() throws Exception {
        ApplicationCase applicationCase = applicationCaseRepository.findById(caseId).orElseThrow();
        applicationCase.setStatus(CaseStatus.APPROVED);
        applicationCaseRepository.save(applicationCase);

        mockMvc.perform(post("/api/cases/{id}/decision", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECT"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decisionReturnsNotFoundForMissingCase() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/decision", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void decisionRequiresDecisionField() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/decision", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Missing decision"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
