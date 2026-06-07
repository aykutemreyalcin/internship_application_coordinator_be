package com.internship.coordinator.service;

import com.internship.coordinator.config.VertexAiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private VertexAiProperties vertexAiProperties;

    @InjectMocks
    private GeminiService geminiService;

    @Test
    void pingReturnsGeminiResponse() {
        when(vertexAiProperties.modelName()).thenReturn("gemini-2.5-flash");
        when(geminiClient.generateText("Reply with exactly one word: pong")).thenReturn("pong");

        var response = geminiService.ping();

        assertEquals("Reply with exactly one word: pong", response.prompt());
        assertEquals("pong", response.response());
        assertEquals("gemini-2.5-flash", response.modelName());
        verify(geminiClient).generateText("Reply with exactly one word: pong");
    }
}
