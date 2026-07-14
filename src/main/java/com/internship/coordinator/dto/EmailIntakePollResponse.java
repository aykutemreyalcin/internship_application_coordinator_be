package com.internship.coordinator.dto;

import java.util.List;
import java.util.UUID;

public record EmailIntakePollResponse(int processedCount, int skippedCount, List<UUID> caseIds) {}
