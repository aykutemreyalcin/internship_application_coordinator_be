package com.internship.coordinator.service;

import com.internship.coordinator.config.VertexAiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(
        properties = {
            "app.vertex-ai.enabled=true",
            "app.vertex-ai.project-id=${GCP_PROJECT_ID}",
            "app.vertex-ai.region=${GCP_REGION:europe-west1}",
            "app.vertex-ai.model-name=${GCP_GEMINI_MODEL:gemini-2.0-flash-001}"
        })
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_LIVE_TEST", matches = "true")
class GeminiLiveIntegrationTest {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private VertexAiProperties vertexAiProperties;

    @Test
    void livePingReturnsGeminiResponse() {
        var response = geminiService.ping();

        assertNotNull(response.response());
        assertFalse(response.response().isBlank());
        assertNotNull(vertexAiProperties.modelName());
    }
}
