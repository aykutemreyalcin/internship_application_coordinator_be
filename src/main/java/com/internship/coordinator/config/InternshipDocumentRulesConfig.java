package com.internship.coordinator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class InternshipDocumentRulesConfig {

    @Bean
    InternshipDocumentRulesProperties internshipDocumentRulesProperties(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            @Value("${app.internship-document-rules.config-path:classpath:internship-document-rules.json}")
                    String configPath)
            throws IOException {
        Resource resource = resourceLoader.getResource(configPath);
        try (InputStream inputStream = resource.getInputStream()) {
            InternshipDocumentRulesFile file =
                    objectMapper.readValue(inputStream, InternshipDocumentRulesFile.class);
            return new InternshipDocumentRulesProperties(
                    configPath, file.learningOutcomesReport(), file.internshipJournal());
        }
    }

    private record InternshipDocumentRulesFile(
            InternshipDocumentRulesProperties.LearningOutcomesReportRules learningOutcomesReport,
            InternshipDocumentRulesProperties.InternshipJournalRules internshipJournal) {}
}
