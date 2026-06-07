package com.internship.coordinator.config;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(VertexAiProperties.class)
public class VertexAiConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
    VertexAI vertexAI(VertexAiProperties properties) {
        validateProperties(properties);
        return new VertexAI(properties.projectId(), properties.region());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
    GenerativeModel generativeModel(VertexAI vertexAI, VertexAiProperties properties) {
        return new GenerativeModel(properties.modelName(), vertexAI);
    }

    private void validateProperties(VertexAiProperties properties) {
        if (!StringUtils.hasText(properties.projectId())) {
            throw new IllegalStateException("app.vertex-ai.project-id is required when Vertex AI is enabled");
        }
        if (!StringUtils.hasText(properties.region())) {
            throw new IllegalStateException("app.vertex-ai.region is required when Vertex AI is enabled");
        }
        if (!StringUtils.hasText(properties.modelName())) {
            throw new IllegalStateException("app.vertex-ai.model-name is required when Vertex AI is enabled");
        }
    }
}
