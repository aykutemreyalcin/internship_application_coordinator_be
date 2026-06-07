package com.internship.coordinator.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.GeneratedClarificationDraft;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.service.ClarificationParseException;
import com.internship.coordinator.service.GeminiClient;
import com.internship.coordinator.service.GeminiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class ClarificationRequestAgent {

    static final String CLARIFICATION_PROMPT =
            """
            You are an internship coordinator assistant at a university.
            Write a professional clarification request email DRAFT to the student about their internship application.
            Return ONLY a JSON object with exactly these keys: subject, body.

            Guidelines:
            - Address the student by name when available.
            - Reference the specific missing or ambiguous fields listed under topicsRequiringClarification.
            - Be polite, concise, and actionable — ask the student to provide the missing information or submit corrected documents.
            - Use plain text in body (no HTML). Include a greeting and sign-off from "Internship Coordination Office".
            - This is a draft for coordinator review — do not claim the email was already sent.

            Application context:
            """;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public ClarificationRequestAgent(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedClarificationDraft generateDraft(
            ApplicationCase applicationCase, ValidationSummaryDto validation, List<String> topics) {
        try {
            String json = geminiClient.generateJson(CLARIFICATION_PROMPT + formatContext(applicationCase, validation, topics));
            GeminiClarificationResponse response =
                    objectMapper.readValue(stripCodeFence(json), GeminiClarificationResponse.class);
            return new GeneratedClarificationDraft(response.subject(), response.body());
        } catch (JsonProcessingException exception) {
            throw new ClarificationParseException("Failed to parse Gemini clarification response", exception);
        } catch (GeminiException exception) {
            throw exception;
        }
    }

    public List<String> collectTopics(ApplicationCase applicationCase, ValidationSummaryDto validation) {
        List<String> topics = new ArrayList<>();
        validation.completeness().issues().forEach(issue -> topics.add(issue.field() + ": " + issue.message()));
        validation.rules().issues().forEach(issue -> topics.add(issue.field() + ": " + issue.message()));
        if (applicationCase.getRecommendation() == Recommendation.CLARIFY
                && StringUtils.hasText(applicationCase.getRecommendationReason())) {
            topics.add("Ambiguity noted: " + applicationCase.getRecommendationReason());
        }
        return topics;
    }

    private String formatContext(
            ApplicationCase applicationCase, ValidationSummaryDto validation, List<String> topics) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("studentName", applicationCase.getStudentName());
        context.put("studentId", applicationCase.getStudentId());
        context.put("fieldOfStudy", applicationCase.getFieldOfStudy());
        context.put("companyName", applicationCase.getCompanyName());
        context.put("supervisorName", applicationCase.getSupervisorName());
        context.put("supervisorEmail", applicationCase.getSupervisorEmail());
        context.put("internshipStartDate", applicationCase.getInternshipStartDate() != null
                ? applicationCase.getInternshipStartDate().toString()
                : null);
        context.put("internshipEndDate", applicationCase.getInternshipEndDate() != null
                ? applicationCase.getInternshipEndDate().toString()
                : null);
        context.put("recommendation", applicationCase.getRecommendation());
        context.put("recommendationReason", applicationCase.getRecommendationReason());
        context.put("validation", validation);
        context.put("topicsRequiringClarification", topics);
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new ClarificationParseException("Failed to build clarification context", exception);
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

    private record GeminiClarificationResponse(String subject, String body) {}
}
