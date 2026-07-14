package com.internship.coordinator.dto;

import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import java.time.Instant;
import java.util.UUID;

public record CaseSummaryResponse(
        UUID caseId,
        CaseStatus status,
        String studentName,
        String studentId,
        String companyName,
        Recommendation recommendation,
        Instant createdAt,
        Instant updatedAt) {
}
