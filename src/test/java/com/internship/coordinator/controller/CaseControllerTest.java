package com.internship.coordinator.controller;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ApplicationDocument;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    private UUID caseId;

    @BeforeEach
    void setUp() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.READY_FOR_REVIEW)
                .studentName("Jan Kowalski")
                .studentId("123456")
                .companyName("Astana Kebab Sp. z o.o.")
                .supervisorName("Anna Nowak")
                .supervisorEmail("supervisor@example.com")
                .fieldOfStudy("Computer Engineering")
                .internshipStartDate(LocalDate.of(2026, 6, 1))
                .internshipEndDate(LocalDate.of(2026, 11, 30))
                .recommendation(Recommendation.APPROVE)
                .recommendationReason("All required fields are present; duration complies with rules.")
                .build();

        ApplicationDocument document = ApplicationDocument.builder()
                .fileName("application.pdf")
                .storagePath("/uploads/application.pdf")
                .pageCount(4)
                .build();
        applicationCase.addDocument(document);

        ValidationResult completeness = ValidationResult.builder()
                .type(ValidationType.COMPLETENESS)
                .passed(true)
                .issues(List.of())
                .build();
        ValidationResult rules = ValidationResult.builder()
                .type(ValidationType.RULES)
                .passed(true)
                .issues(List.of())
                .build();
        applicationCase.addValidationResult(completeness);
        applicationCase.addValidationResult(rules);

        caseId = applicationCaseRepository.save(applicationCase).getCaseId();
    }

    @Test
    void listCasesReturnsPagedSummaries() throws Exception {
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].caseId", is(caseId.toString())))
                .andExpect(jsonPath("$.content[0].studentName", is("Jan Kowalski")))
                .andExpect(jsonPath("$.content[0].companyName", is("Astana Kebab Sp. z o.o.")))
                .andExpect(jsonPath("$.content[0].status", is("READY_FOR_REVIEW")))
                .andExpect(jsonPath("$.content[0].recommendation", is("APPROVE")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void listCasesFiltersByStatusAndSearch() throws Exception {
        mockMvc.perform(get("/api/cases").param("status", "READY_FOR_REVIEW").param("search", "jan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/cases").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/cases").param("search", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void getCaseReturnsContractCompliantDetail() throws Exception {
        mockMvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId", is(caseId.toString())))
                .andExpect(jsonPath("$.status", is("READY_FOR_REVIEW")))
                .andExpect(jsonPath("$.studentName", is("Jan Kowalski")))
                .andExpect(jsonPath("$.studentId", is("123456")))
                .andExpect(jsonPath("$.companyName", is("Astana Kebab Sp. z o.o.")))
                .andExpect(jsonPath("$.supervisorName", is("Anna Nowak")))
                .andExpect(jsonPath("$.supervisorEmail", is("supervisor@example.com")))
                .andExpect(jsonPath("$.fieldOfStudy", is("Computer Engineering")))
                .andExpect(jsonPath("$.internshipStartDate", is("2026-06-01")))
                .andExpect(jsonPath("$.internshipEndDate", is("2026-11-30")))
                .andExpect(jsonPath("$.recommendation", is("APPROVE")))
                .andExpect(jsonPath(
                        "$.recommendationReason",
                        is("All required fields are present; duration complies with rules.")))
                .andExpect(jsonPath("$.validation.completeness.passed", is(true)))
                .andExpect(jsonPath("$.validation.completeness.issues", hasSize(0)))
                .andExpect(jsonPath("$.validation.rules.passed", is(true)))
                .andExpect(jsonPath("$.validation.rules.issues", hasSize(0)))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].fileName", is("application.pdf")))
                .andExpect(jsonPath("$.documents[0].pageCount", is(4)))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getCaseReturnsNotFoundForMissingCase() throws Exception {
        mockMvc.perform(get("/api/cases/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCaseIncludesValidationIssues() throws Exception {
        ApplicationCase applicationCase = applicationCaseRepository.findById(caseId).orElseThrow();
        ValidationResult completeness = applicationCase.getValidationResults().stream()
                .filter(result -> result.getType() == ValidationType.COMPLETENESS)
                .findFirst()
                .orElseThrow();
        completeness.setPassed(false);
        completeness.setIssues(new ArrayList<>(List.of(ValidationIssue.builder()
                .field("studentId")
                .message("Student ID is missing")
                .severity(IssueSeverity.ERROR)
                .build())));
        applicationCaseRepository.save(applicationCase);

        mockMvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.completeness.passed", is(false)))
                .andExpect(jsonPath("$.validation.completeness.issues", hasSize(1)))
                .andExpect(jsonPath("$.validation.completeness.issues[0].field", is("studentId")))
                .andExpect(jsonPath("$.validation.completeness.issues[0].message", is("Student ID is missing")))
                .andExpect(jsonPath("$.validation.completeness.issues[0].severity", is("ERROR")));
    }
}
