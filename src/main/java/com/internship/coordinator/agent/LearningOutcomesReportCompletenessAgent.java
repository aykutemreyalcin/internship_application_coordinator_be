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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LearningOutcomesReportCompletenessAgent {

    static final Set<String> REQUIRED_OUTCOME_CODES = Set.of(
            "W1", "W2", "W3", "W4", "W5",
            "U1", "U2", "U3", "U4", "U5", "U6", "U7", "U8", "U9", "U10", "U11",
            "K01", "K02", "K03", "K05");

    private final ExtractedPayloadService extractedPayloadService;
    private final InternshipDocumentRulesProperties rules;

    public LearningOutcomesReportCompletenessAgent(
            ExtractedPayloadService extractedPayloadService, InternshipDocumentRulesProperties rules) {
        this.extractedPayloadService = extractedPayloadService;
        this.rules = rules;
    }

    public ValidationResult validate(ApplicationCase applicationCase) {
        ExtractedLearningOutcomesReportData payload = extractedPayloadService.readReportPayload(applicationCase);
        List<ValidationIssue> issues = new ArrayList<>();

        checkRequiredString(issues, "studentName", applicationCase.getStudentName(), "Student name is missing");
        checkRequiredString(issues, "studentId", applicationCase.getStudentId(), "Student ID is missing");
        checkRequiredString(
                issues,
                "hostCompanyOrEmployer",
                applicationCase.getCompanyName(),
                "Host company or employer is missing");
        checkRequiredDate(issues, "internshipStartDate", applicationCase.getInternshipStartDate());
        checkRequiredDate(issues, "internshipEndDate", applicationCase.getInternshipEndDate());

        Map<String, LearningOutcomeEntry> outcomesByCode = indexOutcomes(payload.learningOutcomes());
        for (String code : REQUIRED_OUTCOME_CODES) {
            LearningOutcomeEntry entry = outcomesByCode.get(code);
            if (entry == null || !StringUtils.hasText(entry.waysOfAchieving())) {
                issues.add(issue(code + ".waysOfAchieving", "Ways of achieving is missing for outcome " + code));
                continue;
            }
            if (entry.waysOfAchieving().trim().length()
                    < rules.learningOutcomesReport().minWaysOfAchievingLength()) {
                issues.add(issue(
                        code + ".waysOfAchieving",
                        "Ways of achieving for " + code + " is too short (minimum "
                                + rules.learningOutcomesReport().minWaysOfAchievingLength()
                                + " characters)"));
            }
        }

        if (payload.studentSignaturePresent() == null || !payload.studentSignaturePresent()) {
            issues.add(issue("studentSignaturePresent", "Student signature is missing"));
        }

        return ValidationResult.builder()
                .type(ValidationType.COMPLETENESS)
                .passed(issues.isEmpty())
                .issues(issues)
                .build();
    }

    private Map<String, LearningOutcomeEntry> indexOutcomes(List<LearningOutcomeEntry> outcomes) {
        if (outcomes == null) {
            return Map.of();
        }
        return outcomes.stream()
                .filter(entry -> entry.code() != null)
                .collect(Collectors.toMap(
                        entry -> entry.code().trim().toUpperCase(), Function.identity(), (left, right) -> left));
    }

    private void checkRequiredString(
            List<ValidationIssue> issues, String field, String value, String message) {
        if (!StringUtils.hasText(value)) {
            issues.add(issue(field, message));
        }
    }

    private void checkRequiredDate(List<ValidationIssue> issues, String field, java.time.LocalDate value) {
        if (value == null) {
            issues.add(issue(field, field + " is missing"));
        }
    }

    private ValidationIssue issue(String field, String message) {
        return ValidationIssue.builder()
                .field(field)
                .message(message)
                .severity(IssueSeverity.ERROR)
                .build();
    }
}
