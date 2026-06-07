package com.internship.coordinator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TestDatasetProperties.class)
public class TestDatasetConfig {}
