package com.internship.coordinator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedApplicationData(
        String studentName,
        String studentId,
        String fieldOfStudy,
        String companyName,
        String supervisorName,
        String supervisorEmail,
        String internshipStartDate,
        String internshipEndDate) {
}
