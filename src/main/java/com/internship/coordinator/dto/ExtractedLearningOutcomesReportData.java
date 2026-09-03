package com.internship.coordinator.dto;

import java.util.List;

public record ExtractedLearningOutcomesReportData(
        String studentName,
        String studentId,
        String reportDate,
        String hostCompanyOrEmployer,
        String internshipStartDate,
        String internshipEndDate,
        List<LearningOutcomeEntry> learningOutcomes,
        Boolean studentSignaturePresent,
        String supervisorName,
        String supervisorComments,
        Boolean supervisorSignaturePresent,
        String supervisorConfirmationDate,
        Integer ectsCredits,
        Integer recognizedInternshipMonths,
        String allOutcomesAchieved,
        String deanSupervisorComments) {}
