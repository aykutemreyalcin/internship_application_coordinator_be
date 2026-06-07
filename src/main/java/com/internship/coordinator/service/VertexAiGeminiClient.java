package com.internship.coordinator.service;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.internship.coordinator.config.VertexAiProperties;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(GenerativeModel.class)
public class VertexAiGeminiClient implements GeminiClient {

    private final GenerativeModel generativeModel;
    private final VertexAI vertexAI;
    private final VertexAiProperties vertexAiProperties;

    public VertexAiGeminiClient(
            GenerativeModel generativeModel, VertexAI vertexAI, VertexAiProperties vertexAiProperties) {
        this.generativeModel = generativeModel;
        this.vertexAI = vertexAI;
        this.vertexAiProperties = vertexAiProperties;
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

    @Override
    public String generateFromPdf(byte[] pdfBytes, String prompt) {
        try {
            GenerativeModel extractionModel = new GenerativeModel.Builder()
                    .setModelName(vertexAiProperties.modelName())
                    .setVertexAi(vertexAI)
                    .setGenerationConfig(GenerationConfig.newBuilder()
                            .setResponseMimeType("application/json")
                            .build())
                    .build();

            GenerateContentResponse response = extractionModel.generateContent(ContentMaker.fromMultiModalData(
                    prompt, PartMaker.fromMimeTypeAndData("application/pdf", pdfBytes)));
            return ResponseHandler.getText(response);
        } catch (IOException exception) {
            throw new GeminiException("Gemini PDF extraction request failed", exception);
        }
    }
}
