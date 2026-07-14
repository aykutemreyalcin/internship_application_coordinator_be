package com.internship.coordinator.dto;

import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CaseDetailResponse(
        UUID caseId,
        CaseStatus status,
        String studentName,
        String studentId,
        String companyName,
        String supervisorName,
        String supervisorEmail,
        String fieldOfStudy,
        LocalDate internshipStartDate,
        LocalDate internshipEndDate,
        Recommendation recommendation,
        String recommendationReason,
        ValidationSummaryDto validation,
        List<DocumentSummaryDto> documents,
        Instant createdAt,
        Instant updatedAt) {
}
