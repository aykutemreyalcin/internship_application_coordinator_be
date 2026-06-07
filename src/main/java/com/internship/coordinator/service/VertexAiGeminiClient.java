package com.internship.coordinator.service;

import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(GenerativeModel.class)
public class VertexAiGeminiClient implements GeminiClient {

    private final GenerativeModel generativeModel;

    public VertexAiGeminiClient(GenerativeModel generativeModel) {
        this.generativeModel = generativeModel;
    }

    @Override
    public String generateText(String prompt) {
        try {
            GenerateContentResponse response = generativeModel.generateContent(prompt);
            return ResponseHandler.getText(response);
        } catch (IOException exception) {
            throw new GeminiException("Gemini request failed", exception);
        }
    }
}
