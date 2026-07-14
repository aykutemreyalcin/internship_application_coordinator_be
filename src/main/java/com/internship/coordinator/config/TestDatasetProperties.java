package com.internship.coordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.test-dataset")
public record TestDatasetProperties(boolean enabled) {}
