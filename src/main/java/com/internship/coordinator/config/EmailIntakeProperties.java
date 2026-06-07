package com.internship.coordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email-intake")
public record EmailIntakeProperties(boolean enabled, String mode, long pollIntervalSeconds, Imap imap) {

    public record Imap(String host, int port, String username, String password, String folder) {}
}
