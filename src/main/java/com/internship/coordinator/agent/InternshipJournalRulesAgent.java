package com.internship.coordinator.agent;

import com.internship.coordinator.config.InternshipDocumentRulesProperties;
import com.internship.coordinator.dto.ExtractedInternshipJournalData;
import com.internship.coordinator.dto.JournalDayEntry;
import com.internship.coordinator.dto.JournalWeekEntry;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.service.ExtractedPayloadService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InternshipJournalRulesAgent {

    private final ExtractedPayloadService extractedPayloadService;
    private final InternshipDocumentRulesProperties rules;

    public InternshipJournalRulesAgent(
            ExtractedPayloadService extractedPayloadService, InternshipDocumentRulesProperties rules) {
        this.extractedPayloadService = extractedPayloadService;
        this.rules = rules;
    }

    public ValidationResult validate(ApplicationCase applicationCase) {
        ExtractedInternshipJournalData payload = extractedPayloadService.readJournalPayload(applicationCase);
        List<ValidationIssue> issues = new ArrayList<>();

        validateDuration(applicationCase, issues);
        validateWeeklyEntries(applicationCase, payload, issues);

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
        }
    }

    private void validateWeeklyEntries(
            ApplicationCase applicationCase, ExtractedInternshipJournalData payload, List<ValidationIssue> issues) {
        if (payload.weeklyEntries() == null) {
            return;
        }

        int totalHours = 0;
        LocalDate previousWeekEnd = null;
        LocalDate internshipStart = applicationCase.getInternshipStartDate();
        LocalDate internshipEnd = applicationCase.getInternshipEndDate();

        for (int weekIndex = 0; weekIndex < payload.weeklyEntries().size(); weekIndex++) {
            JournalWeekEntry week = payload.weeklyEntries().get(weekIndex);
            int weekNumber = weekIndex + 1;
            int weekHours = 0;

            LocalDate weekStart = parseDate(week == null ? null : week.weekStart());
            LocalDate weekEnd = parseDate(week == null ? null : week.weekEnd());
            if (weekStart != null && weekEnd != null && weekEnd.isBefore(weekStart)) {
                issues.add(error("week" + weekNumber, "Week " + weekNumber + " end date is before start date"));
            }
            if (previousWeekEnd != null && weekStart != null && !weekStart.isAfter(previousWeekEnd)) {
                issues.add(error("week" + weekNumber, "Week " + weekNumber + " overlaps with a previous week"));
            }
            if (internshipStart != null && weekStart != null && weekStart.isBefore(internshipStart)) {
                issues.add(error("week" + weekNumber, "Week " + weekNumber + " starts before internship period"));
            }
            if (internshipEnd != null && weekEnd != null && weekEnd.isAfter(internshipEnd)) {
                issues.add(error("week" + weekNumber, "Week " + weekNumber + " ends after internship period"));
            }
            if (weekEnd != null) {
                previousWeekEnd = weekEnd;
            }

            if (week != null && week.days() != null) {
                for (JournalDayEntry day : week.days()) {
                    if (day == null) {
                        continue;
                    }
                    if (day.workingHours() != null) {
                        weekHours += day.workingHours();
                        totalHours += day.workingHours();
                        if (day.workingHours() > rules.internshipJournal().maxDailyHours()) {
                            issues.add(warning(
                                    "week" + weekNumber + ".workingHours",
                                    "A working day exceeds "
                                            + rules.internshipJournal().maxDailyHours()
                                            + " hours"));
                        }
                    }
                    if (LearningOutcomesReportRulesAgent.isPlaceholder(day.activities())) {
                        issues.add(error(
                                "week" + weekNumber + ".activities",
                                "Activities contain placeholder text"));
                    }
                }
            }

            if (weekHours > 0 && weekHours < rules.internshipJournal().minWeeklyHours()) {
                issues.add(error(
                        "week" + weekNumber + ".totalHours",
                        "Week " + weekNumber + " total hours (" + weekHours + ") is below minimum "
                                + rules.internshipJournal().minWeeklyHours()));
            }
        }

        if (totalHours > 0 && totalHours < rules.internshipJournal().minTotalHours()) {
            issues.add(error(
                    "totalHours",
                    "Total logged hours (" + totalHours + ") is below minimum "
                            + rules.internshipJournal().minTotalHours()));
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
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
