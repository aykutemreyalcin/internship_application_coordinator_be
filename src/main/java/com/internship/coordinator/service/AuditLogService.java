package com.internship.coordinator.service;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.AuditLogEntry;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.ValidationResult;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    public void record(ApplicationCase applicationCase, String actor, String action, String detail) {
        applicationCase.addAuditLogEntry(AuditLogEntry.builder()
                .actor(actor)
                .action(action)
                .detail(detail)
                .build());
    }

    public void recordStatusChange(
            ApplicationCase applicationCase, String actor, CaseStatus fromStatus, CaseStatus toStatus) {
        if (fromStatus == toStatus) {
            return;
        }
        applicationCase.setStatus(toStatus);
        record(applicationCase, actor, "STATUS_" + toStatus.name(), fromStatus.name() + " -> " + toStatus.name());
    }

    public void recordValidationResults(ApplicationCase applicationCase, ValidationResult completeness, ValidationResult rules) {
        record(applicationCase, "Completeness Validation Agent", "VALIDATION_COMPLETENESS", summarizeValidation(completeness));
        record(applicationCase, "University Rules Agent", "VALIDATION_RULES", summarizeValidation(rules));
    }

    private String summarizeValidation(ValidationResult validationResult) {
        if (validationResult.isPassed()) {
            return "passed";
        }
        String issues = validationResult.getIssues().stream()
                .map(issue -> issue.getField() + ": " + issue.getMessage())
                .collect(Collectors.joining("; "));
        return "failed: " + issues;
    }
}
