package com.internship.coordinator.agent;

import com.internship.coordinator.config.UniversityRulesProperties;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ValidationType;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversityRulesAgentTest {

    private UniversityRulesAgent agent;

    @BeforeEach
    void setUp() {
        agent = new UniversityRulesAgent(defaultRules());
    }

    @Test
    void validatePassesForCompliantCase() {
        ApplicationCase applicationCase = compliantCase();

        var result = agent.validate(applicationCase);

        assertEquals(ValidationType.RULES, result.getType());
        assertTrue(result.isPassed());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void validateFailsWhenDurationTooShort() {
        ApplicationCase applicationCase = compliantCase();
        applicationCase.setInternshipEndDate(LocalDate.of(2026, 6, 30));

        var result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertEquals(1, result.getIssues().size());
        assertEquals("internshipEndDate", result.getIssues().getFirst().getField());
        assertTrue(result.getIssues().getFirst().getMessage().contains("at least 84 days"));
    }

    @Test
    void validateFailsWhenDurationTooLong() {
        ApplicationCase applicationCase = compliantCase();
        applicationCase.setInternshipEndDate(LocalDate.of(2027, 6, 1));

        var result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> issue.getMessage().contains("must not exceed 180 days")));
    }

    @Test
    void validateFailsWhenEndDateBeforeStartDate() {
        ApplicationCase applicationCase = compliantCase();
        applicationCase.setInternshipEndDate(LocalDate.of(2026, 5, 1));

        var result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertEquals("internshipEndDate", result.getIssues().getFirst().getField());
        assertEquals("Internship end date must be on or after the start date", result.getIssues().getFirst().getMessage());
    }

    @Test
    void validateFailsWhenStudentIdFormatInvalid() {
        ApplicationCase applicationCase = compliantCase();
        applicationCase.setStudentId("ABC");

        var result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertEquals("studentId", result.getIssues().getFirst().getField());
        assertEquals("Student ID format is invalid", result.getIssues().getFirst().getMessage());
    }

    @Test
    void validateFailsWhenSupervisorEmailFormatInvalid() {
        ApplicationCase applicationCase = compliantCase();
        applicationCase.setSupervisorEmail("not-an-email");

        var result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertEquals("supervisorEmail", result.getIssues().getFirst().getField());
        assertEquals("Supervisor email format is invalid", result.getIssues().getFirst().getMessage());
    }

    @Test
    void validateSkipsDateRulesWhenDatesMissing() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .studentId("123456")
                .supervisorEmail("supervisor@example.com")
                .build();

        var result = agent.validate(applicationCase);

        assertTrue(result.isPassed());
    }

    private ApplicationCase compliantCase() {
        return ApplicationCase.builder()
                .studentId("123456")
                .supervisorEmail("supervisor@example.com")
                .internshipStartDate(LocalDate.of(2026, 6, 1))
                .internshipEndDate(LocalDate.of(2026, 10, 28))
                .build();
    }

    private UniversityRulesProperties defaultRules() {
        return new UniversityRulesProperties(
                new UniversityRulesProperties.InternshipRules(
                        84, 180, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31)),
                new UniversityRulesProperties.FieldFormatRules(
                        "^\\d{5,8}$", "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", true));
    }
}
