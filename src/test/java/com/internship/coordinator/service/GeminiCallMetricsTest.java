package com.internship.coordinator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GeminiCallMetricsTest {

    @AfterEach
    void tearDown() {
        GeminiCallContext.clear();
    }

    @Test
    void toAuditSuffixIncludesLatencyAndTokens() {
        GeminiCallMetrics metrics =
                new GeminiCallMetrics("pdf", "gemini-2.5-flash", 842, 1200, 180, 1380, true);

        String suffix = metrics.toAuditSuffix();

        assertTrue(suffix.contains("op=pdf"));
        assertTrue(suffix.contains("latencyMs=842"));
        assertTrue(suffix.contains("promptTokens=1200"));
        assertTrue(suffix.contains("candidatesTokens=180"));
        assertTrue(suffix.contains("totalTokens=1380"));
        assertTrue(suffix.contains("success=true"));
    }

    @Test
    void appendToDetailLeavesDetailUnchangedWhenMetricsNull() {
        assertEquals("studentName=Jan", GeminiCallMetrics.appendToDetail("studentName=Jan", null));
    }

    @Test
    void appendToDetailAddsSuffix() {
        GeminiCallMetrics metrics = new GeminiCallMetrics("json", "gemini-2.5-flash", 50, 10, 5, 15, true);

        assertEquals(
                "APPROVE: ok | gemini op=json latencyMs=50 success=true promptTokens=10 candidatesTokens=5 totalTokens=15",
                GeminiCallMetrics.appendToDetail("APPROVE: ok", metrics));
    }

    @Test
    void contextStoresAndConsumesOnce() {
        GeminiCallContext.record(new GeminiCallMetrics("text", "model", 1, null, null, null, true));

        assertEquals("text", GeminiCallContext.consume().operation());
        assertNull(GeminiCallContext.consume());
    }
}
