package com.internship.coordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = "http://localhost:5173";
        }
    }
}
