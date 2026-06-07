package com.internship.coordinator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ValidationGroupDto;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.service.GeminiClient;
import java.time.LocalDate;
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
class SupervisorVerificationAgentTest {

    @Mock
    private GeminiClient geminiClient;

    private SupervisorVerificationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new SupervisorVerificationAgent(geminiClient, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void generateDraftReturnsEmailAddressedToSupervisor() {
        ApplicationCase applicationCase = ApplicationCase.builder()
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
        ValidationSummaryDto validation =
                new ValidationSummaryDto(new ValidationGroupDto(true, java.util.List.of()), new ValidationGroupDto(true, java.util.List.of()));

        when(geminiClient.generateJson(anyString()))
                .thenReturn("""
                        {
                          "subject": "Supervisor verification: Jan Kowalski internship at Example GmbH",
                          "body": "Dear Anna Nowak,\\n\\nPlease confirm that you will supervise Jan Kowalski (123456) for the internship at Example GmbH from 2026-06-01 to 2026-10-28.\\n\\nInternship Coordination Office"
                        }
                        """);

        var draft = agent.generateDraft(applicationCase, validation);

        assertEquals("Supervisor verification: Jan Kowalski internship at Example GmbH", draft.subject());
        assertTrue(draft.body().contains("Anna Nowak"));
        assertTrue(draft.body().contains("Jan Kowalski"));
        verify(geminiClient).generateJson(contains("anna.nowak@example.com"));
    }
}
