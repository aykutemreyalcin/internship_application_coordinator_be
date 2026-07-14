package com.internship.coordinator.dto;

import java.util.Map;

public record TestDatasetSeedResponse(
        int total,
        Map<String, Integer> countsByCategory) {}
