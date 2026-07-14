package com.internship.coordinator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ValidationGroupDto;
import com.internship.coordinator.dto.ValidationIssueDto;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.service.GeminiClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClarificationRequestAgentTest {

    @Mock
    private GeminiClient geminiClient;

    private ClarificationRequestAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ClarificationRequestAgent(geminiClient, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void collectTopicsIncludesValidationIssuesAndClarifyReason() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .studentName("Jan Kowalski")
                .recommendation(Recommendation.CLARIFY)
                .recommendationReason("Supervisor email domain does not match company.")
                .build();
        ValidationSummaryDto validation = new ValidationSummaryDto(
                new ValidationGroupDto(
                        false,
                        List.of(new ValidationIssueDto("studentId", "Student ID is missing", IssueSeverity.ERROR))),
                new ValidationGroupDto(true, List.of()));

        List<String> topics = agent.collectTopics(applicationCase, validation);

        assertEquals(2, topics.size());
        assertTrue(topics.contains("studentId: Student ID is missing"));
        assertTrue(topics.contains("Ambiguity noted: Supervisor email domain does not match company."));
    }

    @Test
    void generateDraftReturnsPersonalizedEmailFromGemini() {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .studentName("Jan Kowalski")
                .studentId("123456")
                .build();
        ValidationSummaryDto validation = new ValidationSummaryDto(
                new ValidationGroupDto(
                        false,
                        List.of(new ValidationIssueDto("studentId", "Student ID is missing", IssueSeverity.ERROR))),
                new ValidationGroupDto(true, List.of()));
        List<String> topics = List.of("studentId: Student ID is missing");

        when(geminiClient.generateJson(anyString()))
                .thenReturn("""
                        {
                          "subject": "Action required: missing information in your internship application",
                          "body": "Dear Jan Kowalski,\\n\\nWe reviewed your internship application and need your student ID (123456 area).\\n\\nPlease reply with the missing details.\\n\\nInternship Coordination Office"
                        }
                        """);

        var draft = agent.generateDraft(applicationCase, validation, topics);

        assertEquals("Action required: missing information in your internship application", draft.subject());
        assertTrue(draft.body().contains("Jan Kowalski"));
        verify(geminiClient).generateJson(contains("topicsRequiringClarification"));
    }
}
