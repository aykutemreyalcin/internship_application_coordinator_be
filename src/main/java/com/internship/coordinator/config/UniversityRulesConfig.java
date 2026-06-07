package com.internship.coordinator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class UniversityRulesConfig {

    @Bean
    UniversityRulesProperties universityRulesProperties(
            ObjectMapper objectMapper,
            @Value("${app.university-rules.config-path:classpath:university-rules.json}") Resource configResource)
            throws IOException {
        return objectMapper.readValue(configResource.getInputStream(), UniversityRulesProperties.class);
    }
}
