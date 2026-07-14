package com.internship.coordinator.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.GeneratedRecommendation;
import com.internship.coordinator.dto.ValidationGroupDto;
import com.internship.coordinator.dto.ValidationIssueDto;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.service.GeminiClient;
import com.internship.coordinator.service.GeminiException;
import com.internship.coordinator.service.RecommendationParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class DecisionRecommendationAgent {

    static final String RECOMMENDATION_PROMPT =
            """
            You are an internship application coordinator assistant.
            Review the application fields and validation results below.
            Return ONLY a JSON object with exactly these keys:
            recommendation (one of APPROVE, REJECT, CLARIFY), reason (short explanation for the coordinator).

            Rules:
            - Use APPROVE when the application is complete and compliant.
            - Use CLARIFY when information is ambiguous, inconsistent, or needs human follow-up despite passing checks.
            - Use REJECT only when there are clear grounds to reject beyond the automated validation already listed.
            - Keep reason concise (1-2 sentences).

            Application data and validation:
            """;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public DecisionRecommendationAgent(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedRecommendation recommend(ApplicationCase applicationCase, ValidationSummaryDto validation) {
        GeneratedRecommendation deterministic = tryDeterministicRecommendation(validation);
        if (deterministic != null) {
            return deterministic;
        }
        return callGemini(applicationCase, validation);
    }

    private GeneratedRecommendation tryDeterministicRecommendation(ValidationSummaryDto validation) {
        if (!validation.completeness().passed()) {
            return new GeneratedRecommendation(
                    Recommendation.REJECT, buildIssueReason("Completeness validation failed", validation.completeness()));
        }
        if (!validation.rules().passed()) {
            return new GeneratedRecommendation(
                    Recommendation.REJECT, buildIssueReason("University rules validation failed", validation.rules()));
        }
        return null;
    }

    private GeneratedRecommendation callGemini(ApplicationCase applicationCase, ValidationSummaryDto validation) {
        try {
            String json = geminiClient.generateJson(RECOMMENDATION_PROMPT + formatContext(applicationCase, validation));
            GeminiRecommendationResponse response =
                    objectMapper.readValue(stripCodeFence(json), GeminiRecommendationResponse.class);
            return new GeneratedRecommendation(parseRecommendation(response.recommendation()), response.reason());
        } catch (JsonProcessingException exception) {
            throw new RecommendationParseException("Failed to parse Gemini recommendation response", exception);
        } catch (GeminiException exception) {
            throw exception;
        }
    }

    private String formatContext(ApplicationCase applicationCase, ValidationSummaryDto validation) {
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
        context.put("validation", validation);
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new RecommendationParseException("Failed to build recommendation context", exception);
        }
    }

    private String buildIssueReason(String prefix, ValidationGroupDto group) {
        String issues = group.issues().stream()
                .map(issue -> issue.field() + ": " + issue.message())
                .collect(Collectors.joining("; "));
        return prefix + ": " + issues;
    }

    private Recommendation parseRecommendation(String value) {
        try {
            return Recommendation.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new RecommendationParseException("Invalid recommendation value: " + value, exception);
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

    private record GeminiRecommendationResponse(String recommendation, String reason) {}
}
