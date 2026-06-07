package com.internship.coordinator.dto;

public record ValidationSummaryDto(ValidationGroupDto completeness, ValidationGroupDto rules) {
}
