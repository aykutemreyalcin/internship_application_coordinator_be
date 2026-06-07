package com.internship.coordinator.controller;

import com.internship.coordinator.agent.ClarificationRequestAgent;
import com.internship.coordinator.dto.GeneratedClarificationDraft;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseClarificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @MockitoBean
    private ClarificationRequestAgent clarificationRequestAgent;

    private UUID caseId;

    @BeforeEach
    void setUp() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.READY_FOR_REVIEW)
                .studentName("Jan Kowalski")
                .recommendation(Recommendation.CLARIFY)
                .recommendationReason("Supervisor email domain does not match company.")
                .build();
        caseId = applicationCaseRepository.save(applicationCase).getCaseId();

        when(clarificationRequestAgent.collectTopics(any(), any()))
                .thenReturn(List.of("supervisorEmail: Supervisor email format is invalid"));
        when(clarificationRequestAgent.generateDraft(any(), any(), anyList()))
                .thenReturn(new GeneratedClarificationDraft(
                        "Action required: internship application clarification",
                        "Dear Jan Kowalski,\n\nPlease confirm your supervisor's work email.\n\nInternship Coordination Office"));
    }

    @Test
    void clarificationEndpointReturnsDraftAndUpdatesStatus() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/clarification", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.status").value("CLARIFICATION_REQUESTED"))
                .andExpect(jsonPath("$.studentName").value("Jan Kowalski"))
                .andExpect(jsonPath("$.subject").value("Action required: internship application clarification"))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("Dear Jan Kowalski")));
    }

    @Test
    void clarificationEndpointReturnsBadRequestWhenNothingToClarify() throws Exception {
        when(clarificationRequestAgent.collectTopics(any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/cases/{id}/clarification", caseId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clarificationEndpointReturnsBadRequestForApprovedCase() throws Exception {
        ApplicationCase applicationCase = applicationCaseRepository.findById(caseId).orElseThrow();
        applicationCase.setStatus(CaseStatus.APPROVED);
        applicationCaseRepository.save(applicationCase);

        mockMvc.perform(post("/api/cases/{id}/clarification", caseId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clarificationEndpointReturnsNotFoundForMissingCase() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/clarification", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
