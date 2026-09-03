package com.internship.coordinator.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ExtractedLearningOutcomesReportData;
import com.internship.coordinator.service.ExtractionParseException;
import com.internship.coordinator.service.GeminiClient;
import com.internship.coordinator.service.GeminiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class LearningOutcomesReportExtractionAgent {

    static final String EXTRACTION_PROMPT =
            """
            Extract structured data from this Learning Outcomes Report for a computer engineering internship.
            Return ONLY a JSON object with exactly these keys:
            studentName, studentId, reportDate, hostCompanyOrEmployer, internshipStartDate, internshipEndDate,
            learningOutcomes (array of {code, description, waysOfAchieving}),
            studentSignaturePresent (boolean), supervisorName, supervisorComments,
            supervisorSignaturePresent (boolean), supervisorConfirmationDate,
            ectsCredits (number or null), recognizedInternshipMonths (number or null),
            allOutcomesAchieved ("YES", "NO", or null), deanSupervisorComments.
            Expected outcome codes include W1-W5, U1-U11, K01, K02, K03, K05.
            Use ISO-8601 dates (YYYY-MM-DD). Use null for missing values.
            """;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public LearningOutcomesReportExtractionAgent(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public ExtractedLearningOutcomesReportData extractFromPdf(byte[] pdfBytes) {
        try {
            String json = geminiClient.generateFromPdf(pdfBytes, EXTRACTION_PROMPT);
            return objectMapper.readValue(stripCodeFence(json), ExtractedLearningOutcomesReportData.class);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse report extraction response", exception);
        } catch (GeminiException exception) {
            throw exception;
        }
    }

    public ExtractedLearningOutcomesReportData extractFromText(String documentText) {
        try {
            String json = geminiClient.generateJsonFromDocumentText(documentText, EXTRACTION_PROMPT);
            return objectMapper.readValue(stripCodeFence(json), ExtractedLearningOutcomesReportData.class);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse report extraction response", exception);
        } catch (GeminiException exception) {
            throw exception;
        }
    }

    private String stripCodeFence(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
