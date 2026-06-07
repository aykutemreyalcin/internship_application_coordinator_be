package com.internship.coordinator.service;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.AuditLogEntry;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.IssueSeverity;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogServiceTest {

    private AuditLogService auditLogService;
    private ApplicationCase applicationCase;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService();
        applicationCase = ApplicationCase.builder().status(CaseStatus.NEW).build();
    }

    @Test
    void recordAddsEntryWithActorActionAndDetail() {
        auditLogService.record(applicationCase, "SYSTEM", "CASE_CREATED", "Uploaded application.pdf");

        List<AuditLogEntry> entries = applicationCase.getAuditLogEntries();
        assertEquals(1, entries.size());
        assertEquals("SYSTEM", entries.getFirst().getActor());
        assertEquals("CASE_CREATED", entries.getFirst().getAction());
        assertEquals("Uploaded application.pdf", entries.getFirst().getDetail());
    }

    @Test
    void recordStatusChangeUpdatesStatusAndLogsTransition() {
        auditLogService.recordStatusChange(applicationCase, "COORDINATOR", CaseStatus.NEW, CaseStatus.APPROVED);

        assertEquals(CaseStatus.APPROVED, applicationCase.getStatus());
        AuditLogEntry entry = applicationCase.getAuditLogEntries().getFirst();
        assertEquals("COORDINATOR", entry.getActor());
        assertEquals("STATUS_APPROVED", entry.getAction());
        assertEquals("NEW -> APPROVED", entry.getDetail());
    }

    @Test
    void recordStatusChangeSkipsWhenStatusUnchanged() {
        auditLogService.recordStatusChange(applicationCase, "SYSTEM", CaseStatus.NEW, CaseStatus.NEW);

        assertTrue(applicationCase.getAuditLogEntries().isEmpty());
    }

    @Test
    void recordValidationResultsLogsBothAgents() {
        ValidationResult completeness = ValidationResult.builder()
                .type(ValidationType.COMPLETENESS)
                .passed(true)
                .build();
        ValidationResult rules = ValidationResult.builder()
                .type(ValidationType.RULES)
                .passed(false)
                .issues(List.of(ValidationIssue.builder()
                        .field("internshipEndDate")
                        .message("Duration too short")
                        .severity(IssueSeverity.ERROR)
                        .build()))
                .build();

        auditLogService.recordValidationResults(applicationCase, completeness, rules);

        List<AuditLogEntry> entries = applicationCase.getAuditLogEntries();
        assertEquals(2, entries.size());
        assertEquals("Completeness Validation Agent", entries.get(0).getActor());
        assertEquals("VALIDATION_COMPLETENESS", entries.get(0).getAction());
        assertEquals("passed", entries.get(0).getDetail());
        assertEquals("University Rules Agent", entries.get(1).getActor());
        assertEquals("VALIDATION_RULES", entries.get(1).getAction());
        assertEquals("failed: internshipEndDate: Duration too short", entries.get(1).getDetail());
    }
}
