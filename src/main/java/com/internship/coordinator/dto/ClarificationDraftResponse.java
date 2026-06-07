package com.internship.coordinator.dto;

import com.internship.coordinator.model.CaseStatus;
import java.util.UUID;

public record ClarificationDraftResponse(
        UUID caseId, CaseStatus status, String studentName, String subject, String body) {}
