package com.internship.coordinator.service;

/**
 * Carries the most recent Gemini call metrics on the current thread so CaseService can
 * enrich audit log details without changing every agent return type.
 */
public final class GeminiCallContext {

    private static final ThreadLocal<GeminiCallMetrics> LAST_CALL = new ThreadLocal<>();

    private GeminiCallContext() {}

    public static void clear() {
        LAST_CALL.remove();
    }

    public static void record(GeminiCallMetrics metrics) {
        LAST_CALL.set(metrics);
    }

    /** Returns and clears the last recorded metrics (or {@code null}). */
    public static GeminiCallMetrics consume() {
        GeminiCallMetrics metrics = LAST_CALL.get();
        LAST_CALL.remove();
        return metrics;
    }
}
