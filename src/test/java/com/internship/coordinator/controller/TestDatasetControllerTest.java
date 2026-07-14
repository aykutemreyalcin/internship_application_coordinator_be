package com.internship.coordinator.controller;

import com.internship.coordinator.repository.ApplicationCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.test-dataset.enabled=true")
class TestDatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @Test
    void seedEndpointLoadsFortyCaseDataset() throws Exception {
        mockMvc.perform(post("/api/internal/test-dataset/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(40)))
                .andExpect(jsonPath("$.countsByCategory.VALID", is(10)))
                .andExpect(jsonPath("$.countsByCategory.INCOMPLETE", is(10)))
                .andExpect(jsonPath("$.countsByCategory.RULE_VIOLATION", is(10)))
                .andExpect(jsonPath("$.countsByCategory.AMBIGUOUS", is(10)));

        org.junit.jupiter.api.Assertions.assertEquals(40, applicationCaseRepository.countByDatasetKeyIsNotNull());

        mockMvc.perform(get("/api/internal/test-dataset/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(40)));
    }
}
