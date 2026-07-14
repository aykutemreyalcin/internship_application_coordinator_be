package com.internship.coordinator.dto;

import java.util.UUID;

public record DocumentSummaryDto(UUID id, String fileName, Integer pageCount) {
}
