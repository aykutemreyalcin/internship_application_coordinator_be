package com.internship.coordinator.testdataset;

import java.time.LocalDate;

public record TestDatasetCaseSpec(
        String key,
        TestDatasetCategory category,
        String studentName,
        String studentId,
        String fieldOfStudy,
        String companyName,
        String supervisorName,
        String supervisorEmail,
        LocalDate internshipStartDate,
        LocalDate internshipEndDate,
        String ambiguityNote) {

    public String fileName() {
        return key + ".pdf";
    }
}
