package com.internship.coordinator.controller;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

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
                .internshipEndDate(LocalDate.of(2026, 6, 15))
                .build();
        caseId = applicationCaseRepository.save(applicationCase).getCaseId();
    }

    @Test
    void getValidationReturnsCompletenessAndRulesResults() throws Exception {
        mockMvc.perform(get("/api/cases/{id}/validation", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness.passed", is(true)))
                .andExpect(jsonPath("$.completeness.issues", hasSize(0)))
                .andExpect(jsonPath("$.rules.passed", is(false)))
                .andExpect(jsonPath("$.rules.issues", hasSize(1)))
                .andExpect(jsonPath("$.rules.issues[0].field", is("internshipEndDate")))
                .andExpect(jsonPath("$.rules.issues[0].message", containsString("at least 84 days")))
                .andExpect(jsonPath("$.rules.issues[0].severity", is("ERROR")));
    }

    @Test
    void getValidationReturnsNotFoundForMissingCase() throws Exception {
        mockMvc.perform(get("/api/cases/{id}/validation", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getValidationPersistsRulesResultOnCaseDetail() throws Exception {
        mockMvc.perform(get("/api/cases/{id}/validation", caseId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.rules.passed", is(false)))
                .andExpect(jsonPath("$.validation.rules.issues", hasSize(1)));
    }
}
