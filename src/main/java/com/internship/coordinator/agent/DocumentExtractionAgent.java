package com.internship.coordinator.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.service.ExtractionParseException;
import com.internship.coordinator.service.GeminiClient;
import com.internship.coordinator.service.GeminiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class DocumentExtractionAgent {

    static final String EXTRACTION_PROMPT =
            """
            Extract structured data from this internship application PDF.
            Return ONLY a JSON object with exactly these keys:
            studentName, studentId, fieldOfStudy, companyName, supervisorName, supervisorEmail,
            internshipStartDate, internshipEndDate.
            Use ISO-8601 dates (YYYY-MM-DD). Use null for missing values.
            """;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public DocumentExtractionAgent(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public ExtractedApplicationData extract(byte[] pdfBytes) {
        try {
            String json = geminiClient.generateFromPdf(pdfBytes, EXTRACTION_PROMPT);
            return objectMapper.readValue(stripCodeFence(json), ExtractedApplicationData.class);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse Gemini extraction response", exception);
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
