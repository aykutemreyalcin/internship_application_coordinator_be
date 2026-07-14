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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
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
        return execute("text", () -> {
            GenerateContentResponse response = generativeModel.generateContent(prompt);
            return response;
        });
    }

    @Override
    public String generateJson(String prompt) {
        return execute("json", () -> {
            GenerativeModel jsonModel = new GenerativeModel.Builder()
                    .setModelName(vertexAiProperties.modelName())
                    .setVertexAi(vertexAI)
                    .setGenerationConfig(GenerationConfig.newBuilder()
                            .setResponseMimeType("application/json")
                            .build())
                    .build();
            return jsonModel.generateContent(prompt);
        });
    }

    @Override
    public String generateFromPdf(byte[] pdfBytes, String prompt) {
        return execute("pdf", () -> {
            GenerativeModel extractionModel = new GenerativeModel.Builder()
                    .setModelName(vertexAiProperties.modelName())
                    .setVertexAi(vertexAI)
                    .setGenerationConfig(GenerationConfig.newBuilder()
                            .setResponseMimeType("application/json")
                            .build())
                    .build();

            return extractionModel.generateContent(ContentMaker.fromMultiModalData(
                    prompt, PartMaker.fromMimeTypeAndData("application/pdf", pdfBytes)));
        });
    }

    private String execute(String operation, GeminiCall call) {
        long startedNanos = System.nanoTime();
        String model = vertexAiProperties.modelName();
        log.info("gemini.call.start operation={} model={}", operation, model);
        try {
            GenerateContentResponse response = call.run();
            long latencyMs = elapsedMs(startedNanos);
            GeminiCallMetrics metrics = toMetrics(operation, model, latencyMs, response, true);
            GeminiCallContext.record(metrics);
            log.info(
                    "gemini.call.success operation={} model={} latencyMs={} promptTokens={} candidatesTokens={} totalTokens={}",
                    operation,
                    model,
                    latencyMs,
                    metrics.promptTokens(),
                    metrics.candidatesTokens(),
                    metrics.totalTokens());
            return ResponseHandler.getText(response);
        } catch (IOException exception) {
            long latencyMs = elapsedMs(startedNanos);
            GeminiCallMetrics metrics = new GeminiCallMetrics(operation, model, latencyMs, null, null, null, false);
            GeminiCallContext.record(metrics);
            log.warn(
                    "gemini.call.failed operation={} model={} latencyMs={} error={}",
                    operation,
                    model,
                    latencyMs,
                    exception.getMessage());
            throw new GeminiException("Gemini " + operation + " request failed", exception);
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    static GeminiCallMetrics toMetrics(
            String operation, String model, long latencyMs, GenerateContentResponse response, boolean success) {
        Integer promptTokens = null;
        Integer candidatesTokens = null;
        Integer totalTokens = null;
        if (response != null && response.hasUsageMetadata()) {
            GenerateContentResponse.UsageMetadata usage = response.getUsageMetadata();
            promptTokens = usage.getPromptTokenCount();
            candidatesTokens = usage.getCandidatesTokenCount();
            totalTokens = usage.getTotalTokenCount();
        }
        return new GeminiCallMetrics(operation, model, latencyMs, promptTokens, candidatesTokens, totalTokens, success);
    }

    @FunctionalInterface
    private interface GeminiCall {
        GenerateContentResponse run() throws IOException;
    }
}
