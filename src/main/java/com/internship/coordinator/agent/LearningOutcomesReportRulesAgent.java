package com.internship.coordinator.agent;

import com.internship.coordinator.config.InternshipDocumentRulesProperties;
import com.internship.coordinator.dto.ExtractedLearningOutcomesReportData;
import com.internship.coordinator.dto.LearningOutcomeEntry;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.service.ExtractedPayloadService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LearningOutcomesReportRulesAgent {

    private final ExtractedPayloadService extractedPayloadService;
    private final InternshipDocumentRulesProperties rules;

    public LearningOutcomesReportRulesAgent(
            ExtractedPayloadService extractedPayloadService, InternshipDocumentRulesProperties rules) {
        this.extractedPayloadService = extractedPayloadService;
        this.rules = rules;
    }

    public ValidationResult validate(ApplicationCase applicationCase) {
        ExtractedLearningOutcomesReportData payload = extractedPayloadService.readReportPayload(applicationCase);
        List<ValidationIssue> issues = new ArrayList<>();

        validateDuration(applicationCase, issues);
        validateOutcomes(payload, issues);
        validateSupervisor(payload, issues);
        validateDeanSection(payload, issues);

        return ValidationResult.builder()
                .type(ValidationType.RULES)
                .passed(issues.stream().noneMatch(issue -> issue.getSeverity() == IssueSeverity.ERROR))
                .issues(issues)
                .build();
    }

    private void validateDuration(ApplicationCase applicationCase, List<ValidationIssue> issues) {
        LocalDate startDate = applicationCase.getInternshipStartDate();
        LocalDate endDate = applicationCase.getInternshipEndDate();
        if (startDate == null || endDate == null) {
            return;
        }
        if (endDate.isBefore(startDate)) {
            issues.add(error("internshipEndDate", "Internship end date must be on or after the start date"));
            return;
        }
        long durationDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (durationDays < rules.learningOutcomesReport().minDurationDays()) {
            issues.add(error(
                    "internshipEndDate",
                    "Internship duration must be at least "
                            + rules.learningOutcomesReport().minDurationDays()
                            + " days"));
        }
    }

    private void validateOutcomes(ExtractedLearningOutcomesReportData payload, List<ValidationIssue> issues) {
        if (payload.learningOutcomes() == null) {
            return;
        }
        for (LearningOutcomeEntry entry : payload.learningOutcomes()) {
            if (entry == null || entry.code() == null) {
                continue;
            }
            if (isPlaceholder(entry.waysOfAchieving())) {
                issues.add(error(
                        entry.code() + ".waysOfAchieving",
                        "Ways of achieving for " + entry.code() + " appears to be placeholder text"));
            }
        }
    }

    private void validateSupervisor(ExtractedLearningOutcomesReportData payload, List<ValidationIssue> issues) {
        if (!StringUtils.hasText(payload.supervisorName())) {
            issues.add(error("supervisorName", "Supervisor name is required"));
        }
        if (rules.learningOutcomesReport().requireSupervisorSignature()
                && (payload.supervisorSignaturePresent() == null || !payload.supervisorSignaturePresent())) {
            issues.add(warning("supervisorSignaturePresent", "Supervisor signature is missing"));
        }
    }

    private void validateDeanSection(ExtractedLearningOutcomesReportData payload, List<ValidationIssue> issues) {
        boolean deanSectionFilled = payload.ectsCredits() != null
                || payload.recognizedInternshipMonths() != null
                || StringUtils.hasText(payload.deanSupervisorComments());
        if (!deanSectionFilled) {
            return;
        }
        if (!StringUtils.hasText(payload.allOutcomesAchieved())) {
            issues.add(error(
                    "allOutcomesAchieved",
                    "Dean confirmation must specify YES or NO when Dean section is filled"));
        }
    }

    static boolean isPlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.matches("^[.\\-…_\\s]+$")) {
            return true;
        }
        return trimmed.equalsIgnoreCase("TBD") || trimmed.equalsIgnoreCase("N/A");
    }

    private ValidationIssue error(String field, String message) {
        return ValidationIssue.builder()
                .field(field)
                .message(message)
                .severity(IssueSeverity.ERROR)
                .build();
    }

    private ValidationIssue warning(String field, String message) {
        return ValidationIssue.builder()
                .field(field)
                .message(message)
                .severity(IssueSeverity.WARNING)
                .build();
    }
}
