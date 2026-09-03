package com.internship.coordinator.dto;

import java.util.List;

public record ExtractedInternshipJournalData(
        String studentName,
        String studentId,
        String faculty,
        String fieldOfStudy,
        String studyForm,
        String academicYear,
        String companyName,
        String companyAddress,
        String internshipStartDate,
        String internshipEndDate,
        String companySupervisorName,
        List<JournalWeekEntry> weeklyEntries) {}
