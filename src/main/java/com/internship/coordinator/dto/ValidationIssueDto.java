package com.internship.coordinator.dto;

import com.internship.coordinator.model.IssueSeverity;

public record ValidationIssueDto(String field, String message, IssueSeverity severity) {
}
