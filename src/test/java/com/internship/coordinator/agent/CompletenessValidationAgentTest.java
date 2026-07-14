package com.internship.coordinator.agent;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletenessValidationAgentTest {

    private CompletenessValidationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CompletenessValidationAgent();
    }

    @Test
    void validatePassesWhenAllRequiredFieldsPresent() {
        ApplicationCase applicationCase = completeCase();

        ValidationResult result = agent.validate(applicationCase);

        assertEquals(ValidationType.COMPLETENESS, result.getType());
        assertTrue(result.isPassed());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void validateListsAllMissingFields() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .studentName("Jan Kowalski")
                .build();

        ValidationResult result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertEquals(7, result.getIssues().size());
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("studentId")));
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("supervisorEmail")));
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("internshipEndDate")));
    }

    @Test
    void validateTreatsBlankStringsAsMissing() {
        ApplicationCase applicationCase = completeCase();
        applicationCase.setSupervisorEmail("   ");

        ValidationResult result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertEquals(1, result.getIssues().size());
        assertEquals("supervisorEmail", result.getIssues().getFirst().getField());
        assertEquals("Supervisor email is missing", result.getIssues().getFirst().getMessage());
    }

    private ApplicationCase completeCase() {
        return ApplicationCase.builder()
                .studentName("Jan Kowalski")
                .studentId("123456")
                .fieldOfStudy("Computer Engineering")
                .companyName("Astana Kebab Sp. z o.o.")
                .supervisorName("Anna Nowak")
                .supervisorEmail("supervisor@example.com")
                .internshipStartDate(LocalDate.of(2026, 6, 1))
                .internshipEndDate(LocalDate.of(2026, 11, 30))
                .build();
    }
}
