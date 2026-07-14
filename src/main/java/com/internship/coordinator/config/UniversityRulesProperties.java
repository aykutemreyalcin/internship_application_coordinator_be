package com.internship.coordinator.config;

import java.time.LocalDate;

public record UniversityRulesProperties(InternshipRules internship, FieldFormatRules fields) {

    public record InternshipRules(
            int minDurationDays, int maxDurationDays, LocalDate earliestStartDate, LocalDate latestEndDate) {}

    public record FieldFormatRules(
            String studentIdPattern, String supervisorEmailPattern, boolean requireSupervisorEmailFormat) {}
}
