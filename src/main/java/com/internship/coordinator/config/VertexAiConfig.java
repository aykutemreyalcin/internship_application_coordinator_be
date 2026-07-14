package com.internship.coordinator.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(VertexAiProperties.class)
public class VertexAiConfig {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
    VertexAI vertexAI(VertexAiProperties properties) throws IOException {
        validateProperties(properties);
        VertexAI.Builder builder =
                new VertexAI.Builder().setProjectId(properties.projectId()).setLocation(properties.region());
        if (StringUtils.hasText(properties.credentialsPath())) {
            builder.setCredentials(loadCredentials(properties.credentialsPath()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
    GenerativeModel generativeModel(VertexAI vertexAI, VertexAiProperties properties) {
        return new GenerativeModel(properties.modelName(), vertexAI);
    }

    private GoogleCredentials loadCredentials(String credentialsPath) throws IOException {
        Path path = Path.of(credentialsPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Google credentials file not found: " + path);
        }
        return GoogleCredentials.fromStream(new FileInputStream(path.toFile())).createScoped(CLOUD_PLATFORM_SCOPE);
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
        if (!StringUtils.hasText(properties.credentialsPath())) {
            throw new IllegalStateException(
                    "GOOGLE_APPLICATION_CREDENTIALS is required when Vertex AI is enabled");
        }
    }
}
