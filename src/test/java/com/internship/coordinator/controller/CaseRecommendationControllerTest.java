package com.internship.coordinator.controller;

import com.internship.coordinator.agent.DecisionRecommendationAgent;
import com.internship.coordinator.dto.GeneratedRecommendation;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import java.time.LocalDate;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @MockitoBean
    private DecisionRecommendationAgent decisionRecommendationAgent;

    private UUID caseId;

    @BeforeEach
    void setUp() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.NEW)
                .studentName("Jan Kowalski")
                .studentId("123456")
                .fieldOfStudy("Computer Engineering")
                .companyName("Example GmbH")
                .supervisorName("Anna Nowak")
                .supervisorEmail("supervisor@example.com")
                .internshipStartDate(LocalDate.of(2026, 6, 1))
                .internshipEndDate(LocalDate.of(2026, 10, 28))
                .build();
        caseId = applicationCaseRepository.save(applicationCase).getCaseId();
    }

    @Test
    void recommendationEndpointStoresResultAndSetsReadyForReview() throws Exception {
        when(decisionRecommendationAgent.recommend(any(), any()))
                .thenReturn(new GeneratedRecommendation(
                        Recommendation.APPROVE,
                        "All required fields are present; duration complies with rules."));

        mockMvc.perform(post("/api/cases/{id}/recommendation", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.recommendation").value("APPROVE"))
                .andExpect(jsonPath("$.recommendationReason")
                        .value("All required fields are present; duration complies with rules."));
    }

    @Test
    void recommendationEndpointRejectsWhenValidationFailsDeterministically() throws Exception {
        when(decisionRecommendationAgent.recommend(any(), any()))
                .thenReturn(new GeneratedRecommendation(
                        Recommendation.REJECT, "University rules validation failed: internshipEndDate: too short"));

        mockMvc.perform(post("/api/cases/{id}/recommendation", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.recommendation").value("REJECT"));
    }

    @Test
    void recommendationEndpointReturnsBadRequestForInvalidStatus() throws Exception {
        ApplicationCase applicationCase = applicationCaseRepository.findById(caseId).orElseThrow();
        applicationCase.setStatus(CaseStatus.APPROVED);
        applicationCaseRepository.save(applicationCase);

        mockMvc.perform(post("/api/cases/{id}/recommendation", caseId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recommendationEndpointReturnsNotFoundForMissingCase() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/recommendation", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
