package com.internship.coordinator.dto;

import java.util.List;

public record ValidationGroupDto(boolean passed, List<ValidationIssueDto> issues) {
}
