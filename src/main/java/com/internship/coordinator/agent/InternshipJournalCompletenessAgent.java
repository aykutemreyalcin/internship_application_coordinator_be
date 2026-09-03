package com.internship.coordinator.agent;

import com.internship.coordinator.dto.ExtractedInternshipJournalData;
import com.internship.coordinator.dto.JournalDayEntry;
import com.internship.coordinator.dto.JournalWeekEntry;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.service.ExtractedPayloadService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InternshipJournalCompletenessAgent {

    private final ExtractedPayloadService extractedPayloadService;

    public InternshipJournalCompletenessAgent(ExtractedPayloadService extractedPayloadService) {
        this.extractedPayloadService = extractedPayloadService;
    }

    public ValidationResult validate(ApplicationCase applicationCase) {
        ExtractedInternshipJournalData payload = extractedPayloadService.readJournalPayload(applicationCase);
        List<ValidationIssue> issues = new ArrayList<>();

        checkRequiredString(issues, "faculty", payload.faculty(), "Faculty is missing");
        checkRequiredString(issues, "fieldOfStudy", payload.fieldOfStudy(), "Field of study is missing");
        checkRequiredString(issues, "studentName", applicationCase.getStudentName(), "Student name is missing");
        checkRequiredString(issues, "studentId", applicationCase.getStudentId(), "Student ID is missing");
        checkRequiredString(issues, "studyForm", payload.studyForm(), "Study form is missing");
        checkRequiredString(issues, "academicYear", payload.academicYear(), "Academic year is missing");
        checkRequiredString(issues, "companyName", applicationCase.getCompanyName(), "Company name is missing");
        checkRequiredString(issues, "companyAddress", payload.companyAddress(), "Company address is missing");
        checkRequiredString(
                issues, "companySupervisorName", payload.companySupervisorName(), "Company supervisor name is missing");
        checkRequiredDate(issues, "internshipStartDate", applicationCase.getInternshipStartDate());
        checkRequiredDate(issues, "internshipEndDate", applicationCase.getInternshipEndDate());

        if (payload.weeklyEntries() == null || payload.weeklyEntries().isEmpty()) {
            issues.add(issue("weeklyEntries", "At least one weekly timesheet is required"));
        } else {
            for (int weekIndex = 0; weekIndex < payload.weeklyEntries().size(); weekIndex++) {
                validateWeek(issues, payload.weeklyEntries().get(weekIndex), weekIndex + 1);
            }
        }

        return ValidationResult.builder()
                .type(ValidationType.COMPLETENESS)
                .passed(issues.isEmpty())
                .issues(issues)
                .build();
    }

    private void validateWeek(List<ValidationIssue> issues, JournalWeekEntry week, int weekNumber) {
        if (week == null || week.days() == null || week.days().isEmpty()) {
            issues.add(issue("week" + weekNumber, "Week " + weekNumber + " has no daily entries"));
            return;
        }
        for (int dayIndex = 0; dayIndex < week.days().size(); dayIndex++) {
            JournalDayEntry day = week.days().get(dayIndex);
            String prefix = "week" + weekNumber + ".day" + (dayIndex + 1);
            if (day == null || !StringUtils.hasText(day.date())) {
                issues.add(issue(prefix + ".date", "Working day date is missing in week " + weekNumber));
            }
            if (day == null || day.workingHours() == null || day.workingHours() <= 0) {
                issues.add(issue(prefix + ".workingHours", "Working hours must be greater than zero"));
            }
            if (day == null || !StringUtils.hasText(day.activities())) {
                issues.add(issue(prefix + ".activities", "Activities are missing for a working day"));
            }
        }
    }

    private void checkRequiredString(List<ValidationIssue> issues, String field, String value, String message) {
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
