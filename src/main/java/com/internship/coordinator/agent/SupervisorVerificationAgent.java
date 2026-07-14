package com.internship.coordinator.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.GeneratedSupervisorVerificationDraft;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.service.GeminiClient;
import com.internship.coordinator.service.GeminiException;
import com.internship.coordinator.service.SupervisorVerificationParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class SupervisorVerificationAgent {

    static final String SUPERVISOR_VERIFICATION_PROMPT =
            """
            You are an internship coordinator assistant at a university.
            Write a professional supervisor verification email DRAFT.
            Return ONLY a JSON object with exactly these keys: subject, body.

            Guidelines:
            - Address the supervisor by name. The email will be sent to supervisorEmail from the context.
            - Ask the supervisor to confirm they will supervise the named student for the stated internship period at the listed company.
            - Include key details: student name, student ID, field of study, company, internship start/end dates.
            - Be polite, concise, and professional. Use plain text in body (no HTML).
            - Include greeting and sign-off from "Internship Coordination Office".
            - Ask the supervisor to reply confirming their role or to contact the office with corrections.
            - This is a draft for coordinator review — do not claim the email was already sent.

            Application context:
            """;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public SupervisorVerificationAgent(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedSupervisorVerificationDraft generateDraft(
            ApplicationCase applicationCase, ValidationSummaryDto validation) {
        try {
            String json = geminiClient.generateJson(
                    SUPERVISOR_VERIFICATION_PROMPT + formatContext(applicationCase, validation));
            GeminiSupervisorVerificationResponse response =
                    objectMapper.readValue(stripCodeFence(json), GeminiSupervisorVerificationResponse.class);
            return new GeneratedSupervisorVerificationDraft(response.subject(), response.body());
        } catch (JsonProcessingException exception) {
            throw new SupervisorVerificationParseException(
                    "Failed to parse Gemini supervisor verification response", exception);
        } catch (GeminiException exception) {
            throw exception;
        }
    }

    private String formatContext(ApplicationCase applicationCase, ValidationSummaryDto validation) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("supervisorName", applicationCase.getSupervisorName());
        context.put("supervisorEmail", applicationCase.getSupervisorEmail());
        context.put("studentName", applicationCase.getStudentName());
        context.put("studentId", applicationCase.getStudentId());
        context.put("fieldOfStudy", applicationCase.getFieldOfStudy());
        context.put("companyName", applicationCase.getCompanyName());
        context.put("internshipStartDate", applicationCase.getInternshipStartDate() != null
                ? applicationCase.getInternshipStartDate().toString()
                : null);
        context.put("internshipEndDate", applicationCase.getInternshipEndDate() != null
                ? applicationCase.getInternshipEndDate().toString()
                : null);
        context.put("recommendation", applicationCase.getRecommendation());
        context.put("recommendationReason", applicationCase.getRecommendationReason());
        context.put("validation", validation);
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new SupervisorVerificationParseException(
                    "Failed to build supervisor verification context", exception);
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

    private record GeminiSupervisorVerificationResponse(String subject, String body) {}
}
