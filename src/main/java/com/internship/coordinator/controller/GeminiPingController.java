package com.internship.coordinator.controller;

import com.internship.coordinator.dto.GeminiPingResponse;
import com.internship.coordinator.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/gemini")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.vertex-ai", name = "enabled", havingValue = "true")
public class GeminiPingController {

    private final GeminiService geminiService;

    @GetMapping("/ping")
    public GeminiPingResponse ping() {
        return geminiService.ping();
    }
}
