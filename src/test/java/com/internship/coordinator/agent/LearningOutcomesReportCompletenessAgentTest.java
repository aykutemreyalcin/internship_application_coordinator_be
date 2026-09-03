package com.internship.coordinator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.config.InternshipDocumentRulesProperties;
import com.internship.coordinator.dto.ExtractedLearningOutcomesReportData;
import com.internship.coordinator.dto.LearningOutcomeEntry;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseType;
import com.internship.coordinator.service.ExtractedPayloadService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningOutcomesReportCompletenessAgentTest {

    private LearningOutcomesReportCompletenessAgent agent;
    private ExtractedPayloadService extractedPayloadService;

    @BeforeEach
    void setUp() {
        extractedPayloadService = new ExtractedPayloadService(new ObjectMapper());
        agent = new LearningOutcomesReportCompletenessAgent(extractedPayloadService, documentRules());
    }

    @Test
    void validateFailsWhenOutcomeMissing() {
        ApplicationCase applicationCase = reportCase(List.of(new LearningOutcomeEntry(
                "W1", "Description", "Concrete activities performed during the first internship week")));

        var result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("W2.waysOfAchieving")));
    }

    @Test
    void validateFailsWhenStudentSignatureMissing() {
        ApplicationCase applicationCase = reportCase(List.of(new LearningOutcomeEntry(
                "W1",
                "Description",
                "Concrete activities performed during the first internship week with sufficient detail")));
        applicationCase.setExtractedPayload(null);
        extractedPayloadService.storeReportPayload(
                applicationCase,
                new ExtractedLearningOutcomesReportData(
                        "Jan Kowalski",
                        "123456",
                        "2026-11-30",
                        "Example Corp",
                        "2026-06-01",
                        "2026-11-30",
                        List.of(new LearningOutcomeEntry(
                                "W1",
                                "Description",
                                "Concrete activities performed during the first internship week with sufficient detail")),
                        false,
                        "Anna Nowak",
                        null,
                        true,
                        "2026-11-29",
                        null,
                        null,
                        null,
                        null));

        var result = agent.validate(applicationCase);

        assertFalse(result.isPassed());
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("studentSignaturePresent")));
    }

    private ApplicationCase reportCase(List<LearningOutcomeEntry> outcomes) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .caseType(CaseType.LEARNING_OUTCOMES_REPORT)
                .studentName("Jan Kowalski")
                .studentId("123456")
                .companyName("Example Corp")
                .internshipStartDate(LocalDate.of(2026, 6, 1))
                .internshipEndDate(LocalDate.of(2026, 11, 30))
                .build();
        extractedPayloadService.storeReportPayload(
                applicationCase,
                new ExtractedLearningOutcomesReportData(
                        "Jan Kowalski",
                        "123456",
                        "2026-11-30",
                        "Example Corp",
                        "2026-06-01",
                        "2026-11-30",
                        outcomes,
                        true,
                        "Anna Nowak",
                        null,
                        true,
                        "2026-11-29",
                        null,
                        null,
                        null,
                        null));
        return applicationCase;
    }

    private InternshipDocumentRulesProperties documentRules() {
        return new InternshipDocumentRulesProperties(
                "classpath:internship-document-rules.json",
                new InternshipDocumentRulesProperties.LearningOutcomesReportRules(84, 20, true),
                new InternshipDocumentRulesProperties.InternshipJournalRules(30, 8, 360, 10));
    }
}
