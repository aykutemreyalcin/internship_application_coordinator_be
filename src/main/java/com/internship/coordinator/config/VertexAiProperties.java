package com.internship.coordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vertex-ai")
public record VertexAiProperties(boolean enabled, String projectId, String region, String modelName) {
}
