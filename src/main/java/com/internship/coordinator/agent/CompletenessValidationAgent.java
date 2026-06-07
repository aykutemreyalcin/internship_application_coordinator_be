package com.internship.coordinator.agent;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CompletenessValidationAgent {

    public ValidationResult validate(ApplicationCase applicationCase) {
        List<ValidationIssue> issues = new ArrayList<>();
        checkRequiredString(issues, "studentName", applicationCase.getStudentName(), "Student name is missing");
        checkRequiredString(issues, "studentId", applicationCase.getStudentId(), "Student ID is missing");
        checkRequiredString(issues, "fieldOfStudy", applicationCase.getFieldOfStudy(), "Field of study is missing");
        checkRequiredString(issues, "companyName", applicationCase.getCompanyName(), "Company name is missing");
        checkRequiredString(issues, "supervisorName", applicationCase.getSupervisorName(), "Supervisor name is missing");
        checkRequiredString(
                issues, "supervisorEmail", applicationCase.getSupervisorEmail(), "Supervisor email is missing");
        checkRequiredDate(
                issues,
                "internshipStartDate",
                applicationCase.getInternshipStartDate(),
                "Internship start date is missing");
        checkRequiredDate(
                issues, "internshipEndDate", applicationCase.getInternshipEndDate(), "Internship end date is missing");

        return ValidationResult.builder()
                .type(ValidationType.COMPLETENESS)
                .passed(issues.isEmpty())
                .issues(issues)
                .build();
    }

    private void checkRequiredString(List<ValidationIssue> issues, String field, String value, String message) {
        if (value == null || value.isBlank()) {
            issues.add(buildIssue(field, message));
        }
    }

    private void checkRequiredDate(List<ValidationIssue> issues, String field, LocalDate value, String message) {
        if (value == null) {
            issues.add(buildIssue(field, message));
        }
    }

    private ValidationIssue buildIssue(String field, String message) {
        return ValidationIssue.builder()
                .field(field)
                .message(message)
                .severity(IssueSeverity.ERROR)
                .build();
    }
}
