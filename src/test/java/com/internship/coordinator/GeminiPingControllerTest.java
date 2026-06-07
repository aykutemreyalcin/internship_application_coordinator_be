package com.internship.coordinator;

import com.internship.coordinator.controller.GeminiPingController;
import com.internship.coordinator.dto.GeminiPingResponse;
import com.internship.coordinator.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GeminiPingController.class)
@TestPropertySource(
        properties = {
            "app.vertex-ai.enabled=true",
            "app.vertex-ai.project-id=test-project",
            "app.vertex-ai.region=europe-west1",
            "app.vertex-ai.model-name=gemini-2.0-flash-001"
        })
class GeminiPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiService geminiService;

    @Test
    void pingEndpointReturnsGeminiResponse() throws Exception {
        when(geminiService.ping())
                .thenReturn(new GeminiPingResponse("Reply with exactly one word: pong", "pong", "gemini-2.0-flash-001"));

        mockMvc.perform(get("/api/internal/gemini/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt", org.hamcrest.Matchers.is("Reply with exactly one word: pong")))
                .andExpect(jsonPath("$.response", org.hamcrest.Matchers.is("pong")))
                .andExpect(jsonPath("$.modelName", org.hamcrest.Matchers.is("gemini-2.0-flash-001")));
    }
}
