package com.internship.coordinator.dto;

import com.internship.coordinator.model.CaseStatus;
import java.util.UUID;

public record SupervisorVerificationDraftResponse(
        UUID caseId,
        CaseStatus status,
        String supervisorName,
        String supervisorEmail,
        String subject,
        String body) {}
