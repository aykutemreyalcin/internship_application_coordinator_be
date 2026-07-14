package com.internship.coordinator.service;

/**
 * Lightweight metrics for a single Vertex AI / Gemini call.
 * Surfaced in structured logs and appended to audit log details.
 */
public record GeminiCallMetrics(
        String operation,
        String model,
        long latencyMs,
        Integer promptTokens,
        Integer candidatesTokens,
        Integer totalTokens,
        boolean success) {

    public String toAuditSuffix() {
        StringBuilder suffix = new StringBuilder();
        suffix.append(" | gemini op=")
                .append(operation)
                .append(" latencyMs=")
                .append(latencyMs)
                .append(" success=")
                .append(success);
        if (promptTokens != null) {
            suffix.append(" promptTokens=").append(promptTokens);
        }
        if (candidatesTokens != null) {
            suffix.append(" candidatesTokens=").append(candidatesTokens);
        }
        if (totalTokens != null) {
            suffix.append(" totalTokens=").append(totalTokens);
        }
        return suffix.toString();
    }

    public static String appendToDetail(String detail, GeminiCallMetrics metrics) {
        if (metrics == null) {
            return detail;
        }
        return detail + metrics.toAuditSuffix();
    }
}
