package com.internship.coordinator.service;

import com.internship.coordinator.config.VertexAiProperties;
import com.internship.coordinator.dto.GeminiPingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class GeminiService {

    private static final String PING_PROMPT = "Reply with exactly one word: pong";

    private final GeminiClient geminiClient;
    private final VertexAiProperties vertexAiProperties;

    public GeminiPingResponse ping() {
        String response = geminiClient.generateText(PING_PROMPT);
        return new GeminiPingResponse(PING_PROMPT, response, vertexAiProperties.modelName());
    }
}
