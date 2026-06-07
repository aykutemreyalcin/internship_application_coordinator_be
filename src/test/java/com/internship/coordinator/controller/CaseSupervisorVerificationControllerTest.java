package com.internship.coordinator.controller;

import com.internship.coordinator.agent.SupervisorVerificationAgent;
import com.internship.coordinator.dto.GeneratedSupervisorVerificationDraft;
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
class CaseSupervisorVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @MockitoBean
    private SupervisorVerificationAgent supervisorVerificationAgent;

    private UUID caseId;

    @BeforeEach
    void setUp() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.READY_FOR_REVIEW)
                .studentName("Jan Kowalski")
                .studentId("123456")
                .fieldOfStudy("Computer Engineering")
                .companyName("Example GmbH")
                .supervisorName("Anna Nowak")
                .supervisorEmail("anna.nowak@example.com")
                .internshipStartDate(LocalDate.of(2026, 6, 1))
                .internshipEndDate(LocalDate.of(2026, 10, 28))
                .recommendation(Recommendation.APPROVE)
                .build();
        caseId = applicationCaseRepository.save(applicationCase).getCaseId();

        when(supervisorVerificationAgent.generateDraft(any(), any()))
                .thenReturn(new GeneratedSupervisorVerificationDraft(
                        "Supervisor verification: Jan Kowalski internship",
                        "Dear Anna Nowak,\n\nPlease confirm you will supervise Jan Kowalski.\n\nInternship Coordination Office"));
    }

    @Test
    void supervisorVerificationEndpointReturnsDraftAndUpdatesStatus() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/supervisor-verification", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_SUPERVISOR"))
                .andExpect(jsonPath("$.supervisorName").value("Anna Nowak"))
                .andExpect(jsonPath("$.supervisorEmail").value("anna.nowak@example.com"))
                .andExpect(jsonPath("$.subject").value("Supervisor verification: Jan Kowalski internship"))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("Dear Anna Nowak")));
    }

    @Test
    void supervisorVerificationEndpointReturnsBadRequestWhenSupervisorEmailMissing() throws Exception {
        ApplicationCase applicationCase = applicationCaseRepository.findById(caseId).orElseThrow();
        applicationCase.setSupervisorEmail(null);
        applicationCaseRepository.save(applicationCase);

        mockMvc.perform(post("/api/cases/{id}/supervisor-verification", caseId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supervisorVerificationEndpointReturnsBadRequestForApprovedCase() throws Exception {
        ApplicationCase applicationCase = applicationCaseRepository.findById(caseId).orElseThrow();
        applicationCase.setStatus(CaseStatus.APPROVED);
        applicationCaseRepository.save(applicationCase);

        mockMvc.perform(post("/api/cases/{id}/supervisor-verification", caseId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supervisorVerificationEndpointReturnsNotFoundForMissingCase() throws Exception {
        mockMvc.perform(post("/api/cases/{id}/supervisor-verification", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
