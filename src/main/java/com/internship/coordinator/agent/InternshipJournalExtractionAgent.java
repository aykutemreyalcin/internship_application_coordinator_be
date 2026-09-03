package com.internship.coordinator.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ExtractedInternshipJournalData;
import com.internship.coordinator.service.ExtractionParseException;
import com.internship.coordinator.service.GeminiClient;
import com.internship.coordinator.service.GeminiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class InternshipJournalExtractionAgent {

    static final String EXTRACTION_PROMPT =
            """
            Extract structured data from this Student Internship Journal document.
            IMPORTANT: First read the header/metadata section at the top of the document (student name, student ID,
            faculty, field of study, study form, academic year, company, company address, supervisor, internship dates).
            These fields are often in labeled form fields or the first table rows before the weekly timesheets.
            Return ONLY a JSON object with exactly these keys:
            studentName, studentId, faculty, fieldOfStudy, studyForm (FULL_TIME or PART_TIME),
            academicYear, companyName, companyAddress, internshipStartDate, internshipEndDate,
            companySupervisorName,
            weeklyEntries (array of {weekStart, weekEnd, days: [{date, hoursFrom, hoursTo, workingHours, activities}], supervisorSignaturePresent}).
            Use ISO-8601 dates (YYYY-MM-DD). Use null for missing values.
            """;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public InternshipJournalExtractionAgent(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public ExtractedInternshipJournalData extractFromPdf(byte[] pdfBytes) {
        try {
            String json = geminiClient.generateFromPdf(pdfBytes, EXTRACTION_PROMPT);
            return objectMapper.readValue(stripCodeFence(json), ExtractedInternshipJournalData.class);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse journal extraction response", exception);
        } catch (GeminiException exception) {
            throw exception;
        }
    }

    public ExtractedInternshipJournalData extractFromDocx(byte[] docxBytes) {
        try {
            String json = geminiClient.generateFromDocx(docxBytes, EXTRACTION_PROMPT);
            return objectMapper.readValue(stripCodeFence(json), ExtractedInternshipJournalData.class);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse journal extraction response", exception);
        } catch (GeminiException exception) {
            throw exception;
        }
    }

    public ExtractedInternshipJournalData extractFromText(String documentText) {
        try {
            String json = geminiClient.generateJsonFromDocumentText(documentText, EXTRACTION_PROMPT);
            return objectMapper.readValue(stripCodeFence(json), ExtractedInternshipJournalData.class);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse journal extraction response", exception);
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
