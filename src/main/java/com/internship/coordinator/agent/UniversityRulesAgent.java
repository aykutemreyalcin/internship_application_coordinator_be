package com.internship.coordinator.agent;

import com.internship.coordinator.config.UniversityRulesProperties;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UniversityRulesAgent {

    private final UniversityRulesProperties rules;
    private final Pattern studentIdPattern;
    private final Pattern supervisorEmailPattern;

    public UniversityRulesAgent(UniversityRulesProperties rules) {
        this.rules = rules;
        this.studentIdPattern = compilePattern(rules.fields().studentIdPattern());
        this.supervisorEmailPattern = compilePattern(rules.fields().supervisorEmailPattern());
    }

    public ValidationResult validate(ApplicationCase applicationCase) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateInternshipRules(applicationCase, issues);
        validateFieldFormats(applicationCase, issues);

        return ValidationResult.builder()
                .type(ValidationType.RULES)
                .passed(issues.isEmpty())
                .issues(issues)
                .build();
    }

    private void validateInternshipRules(ApplicationCase applicationCase, List<ValidationIssue> issues) {
        LocalDate startDate = applicationCase.getInternshipStartDate();
        LocalDate endDate = applicationCase.getInternshipEndDate();
        if (startDate == null || endDate == null) {
            return;
        }

        if (endDate.isBefore(startDate)) {
            issues.add(issue(
                    "internshipEndDate",
                    "Internship end date must be on or after the start date"));
            return;
        }

        UniversityRulesProperties.InternshipRules internshipRules = rules.internship();
        if (startDate.isBefore(internshipRules.earliestStartDate())) {
            issues.add(issue(
                    "internshipStartDate",
                    "Internship start date must be on or after "
                            + internshipRules.earliestStartDate()));
        }
        if (endDate.isAfter(internshipRules.latestEndDate())) {
            issues.add(issue(
                    "internshipEndDate",
                    "Internship end date must be on or before " + internshipRules.latestEndDate()));
        }

        long durationDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (durationDays < internshipRules.minDurationDays()) {
            issues.add(issue(
                    "internshipEndDate",
                    "Internship duration must be at least "
                            + internshipRules.minDurationDays()
                            + " days (actual: "
                            + durationDays
                            + ")"));
        }
        if (durationDays > internshipRules.maxDurationDays()) {
            issues.add(issue(
                    "internshipEndDate",
                    "Internship duration must not exceed "
                            + internshipRules.maxDurationDays()
                            + " days (actual: "
                            + durationDays
                            + ")"));
        }
    }

    private void validateFieldFormats(ApplicationCase applicationCase, List<ValidationIssue> issues) {
        String studentId = applicationCase.getStudentId();
        if (StringUtils.hasText(studentId) && studentIdPattern != null && !studentIdPattern.matcher(studentId).matches()) {
            issues.add(issue("studentId", "Student ID format is invalid"));
        }

        String supervisorEmail = applicationCase.getSupervisorEmail();
        if (rules.fields().requireSupervisorEmailFormat()
                && StringUtils.hasText(supervisorEmail)
                && supervisorEmailPattern != null
                && !supervisorEmailPattern.matcher(supervisorEmail).matches()) {
            issues.add(issue("supervisorEmail", "Supervisor email format is invalid"));
        }
    }

    private Pattern compilePattern(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return null;
        }
        return Pattern.compile(pattern);
    }

    private ValidationIssue issue(String field, String message) {
        return ValidationIssue.builder()
                .field(field)
                .message(message)
                .severity(IssueSeverity.ERROR)
                .build();
    }
}
