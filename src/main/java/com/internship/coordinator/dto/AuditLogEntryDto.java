package com.internship.coordinator.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogEntryDto(UUID id, String actor, String action, String detail, Instant timestamp) {}
