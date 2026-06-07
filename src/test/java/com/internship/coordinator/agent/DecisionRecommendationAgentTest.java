package com.internship.coordinator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ValidationGroupDto;
import com.internship.coordinator.dto.ValidationIssueDto;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.service.GeminiClient;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionRecommendationAgentTest {

    @Mock
    private GeminiClient geminiClient;

    private DecisionRecommendationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DecisionRecommendationAgent(geminiClient, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void recommendRejectsDeterministicallyWhenCompletenessFails() {
        ApplicationCase applicationCase = compliantCase();
        ValidationSummaryDto validation = new ValidationSummaryDto(
                new ValidationGroupDto(
                        false,
                        List.of(new ValidationIssueDto("studentId", "Student ID is missing", IssueSeverity.ERROR))),
                passingRules());

        var result = agent.recommend(applicationCase, validation);

        assertEquals(Recommendation.REJECT, result.recommendation());
        assertEquals(
                "Completeness validation failed: studentId: Student ID is missing",
                result.reason());
        verifyNoInteractions(geminiClient);
    }

    @Test
    void recommendRejectsDeterministicallyWhenRulesFail() {
        ApplicationCase applicationCase = compliantCase();
        ValidationSummaryDto validation = new ValidationSummaryDto(
                passingCompleteness(),
                new ValidationGroupDto(
                        false,
                        List.of(new ValidationIssueDto(
                                "internshipEndDate",
                                "Internship duration must be at least 84 days (actual: 15)",
                                IssueSeverity.ERROR))));

        var result = agent.recommend(applicationCase, validation);

        assertEquals(Recommendation.REJECT, result.recommendation());
        assertEquals(
                "University rules validation failed: internshipEndDate: Internship duration must be at least 84 days (actual: 15)",
                result.reason());
        verifyNoInteractions(geminiClient);
    }

    @Test
    void recommendCallsGeminiWhenValidationPasses() {
        ApplicationCase applicationCase = compliantCase();
        ValidationSummaryDto validation = new ValidationSummaryDto(passingCompleteness(), passingRules());

        when(geminiClient.generateJson(anyString()))
                .thenReturn("""
                        {"recommendation":"APPROVE","reason":"All required fields are present; duration complies with rules."}
                        """);

        var result = agent.recommend(applicationCase, validation);

        assertEquals(Recommendation.APPROVE, result.recommendation());
        assertEquals(
                "All required fields are present; duration complies with rules.",
                result.reason());
        verify(geminiClient).generateJson(contains("studentName"));
    }

    @Test
    void recommendReturnsClarifyFromGeminiWhenAmbiguous() {
        ApplicationCase applicationCase = compliantCase();
        ValidationSummaryDto validation = new ValidationSummaryDto(passingCompleteness(), passingRules());

        when(geminiClient.generateJson(anyString()))
                .thenReturn("""
                        {"recommendation":"CLARIFY","reason":"Supervisor email domain does not match company name."}
                        """);

        var result = agent.recommend(applicationCase, validation);

        assertEquals(Recommendation.CLARIFY, result.recommendation());
        assertEquals("Supervisor email domain does not match company name.", result.reason());
    }

    private ApplicationCase compliantCase() {
        return ApplicationCase.builder()
                .studentName("Jan Kowalski")
                .studentId("123456")
                .fieldOfStudy("Computer Engineering")
                .companyName("Example GmbH")
                .supervisorName("Anna Nowak")
                .supervisorEmail("supervisor@example.com")
                .internshipStartDate(LocalDate.of(2026, 6, 1))
                .internshipEndDate(LocalDate.of(2026, 10, 28))
                .build();
    }

    private ValidationGroupDto passingCompleteness() {
        return new ValidationGroupDto(true, List.of());
    }

    private ValidationGroupDto passingRules() {
        return new ValidationGroupDto(true, List.of());
    }
}
