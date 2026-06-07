package com.internship.coordinator.dto;

import com.internship.coordinator.model.Recommendation;
import jakarta.validation.constraints.NotNull;

public record CoordinatorDecisionRequest(@NotNull Recommendation decision, String note) {}
